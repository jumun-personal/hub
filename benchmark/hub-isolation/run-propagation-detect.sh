#!/usr/bin/env bash
set -euo pipefail

kind="${1:?idle or load is required}"
run_number="${2:-1}"

if [[ "${kind}" != "idle" && "${kind}" != "load" ]]; then
  echo "kind must be idle or load" >&2
  exit 2
fi

read_rps="${HUB_READ_RPS:-150}"
read_duration="${HUB_READ_DURATION:-180s}"
warmup_seconds="${HUB_READ_WARMUP_SECONDS:-120}"
trigger_after_seconds="${HUB_ROUTE_TRIGGER_AFTER_SECONDS:-30}"
pair_count="${ROUTE_BUILD_PAIR_COUNT:-100}"
base_url="${HUB_BASE_URL:-http://localhost:18088}"
hub_id="00000000-0000-0000-0000-000000000001"
timestamp="$(date +%Y%m%d-%H%M%S)"
result_dir="benchmark/hub-isolation/results/propagation-${kind}-run${run_number}-${timestamp}"
compose_file="benchmark/hub-isolation/rps-baseline-compose.yml"

mkdir -p "${result_dir}"

cleanup() {
  docker compose -f "${compose_file}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

cleanup

{
  echo "kind=${kind}"
  echo "run=${run_number}"
  echo "read_rps=${read_rps}"
  echo "read_duration=${read_duration}"
  echo "warmup_seconds=${warmup_seconds}"
  echo "trigger_after_seconds=${trigger_after_seconds}"
  echo "pair_count=${pair_count}"
  echo "mem_limit=${HUB_BENCHMARK_MEM_LIMIT:-1g}"
  echo "api_hikari=${HUB_BENCHMARK_HIKARI_POOL_SIZE:-20}"
  echo "heap_xms=${HUB_BENCHMARK_XMS:--Xms256m}"
  echo "heap_xmx=${HUB_BENCHMARK_XMX:--Xmx256m}"
  echo "gc=${HUB_BENCHMARK_GC_OPTION:--XX:+UseG1GC}"
  git rev-parse HEAD
  shasum -a 256 build/libs/hub_prodcut_stock_company-0.0.1-SNAPSHOT.jar
} > "${result_dir}/metadata.txt"

HUB_ROUTE_BUILD_SCHEDULER_ENABLED=true \
HUB_ROUTE_BUILD_BATCH_SIZE="${HUB_ROUTE_BUILD_BATCH_SIZE:-10}" \
HUB_ROUTE_BUILD_FIXED_DELAY_MS="${HUB_ROUTE_BUILD_FIXED_DELAY_MS:-500}" \
HUB_BENCHMARK_HIKARI_POOL_SIZE="${HUB_BENCHMARK_HIKARI_POOL_SIZE:-20}" \
HUB_BENCHMARK_XMS="${HUB_BENCHMARK_XMS:--Xms256m}" \
HUB_BENCHMARK_XMX="${HUB_BENCHMARK_XMX:--Xmx256m}" \
HUB_BENCHMARK_GC_OPTION="${HUB_BENCHMARK_GC_OPTION:--XX:+UseG1GC}" \
docker compose -f "${compose_file}" up -d --wait

for _ in {1..120}; do
  http_status="$(curl --max-time 2 -s -o /dev/null -w '%{http_code}' "${base_url}/actuator/health" || true)"
  if [[ "${http_status}" != "000" ]]; then
    break
  fi
  sleep 1
done
if [[ "${http_status}" == "000" ]]; then
  docker logs hub-rps-api > "${result_dir}/api-startup.log" 2>&1 || true
  echo "hub-api did not open its HTTP port" >&2
  exit 1
fi

docker exec -i hub-rps-postgres \
  psql -U testuser -d testdb -v ON_ERROR_STOP=1 \
  < benchmark/hub-isolation/seed-routes.sql \
  > "${result_dir}/seed.log"

docker exec hub-rps-postgres \
  psql -U testuser -d testdb -v ON_ERROR_STOP=1 \
  -c "INSERT INTO p_hub_route_build_job (
          hub_id, status, total_count, remaining_count, failed_count, retry_count,
          next_retry_at, created_at, modified_at, is_deleted
      )
      VALUES (
          '${hub_id}'::uuid, 'RUNNING', ${pair_count}, ${pair_count}, 0, 0,
          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false
      )
      ON CONFLICT (hub_id)
      DO UPDATE SET
          status = 'RUNNING',
          total_count = ${pair_count},
          remaining_count = ${pair_count},
          failed_count = 0,
          retry_count = 0,
          next_retry_at = CURRENT_TIMESTAMP,
          modified_at = CURRENT_TIMESTAMP,
          is_deleted = false;" \
  > "${result_dir}/seed-job.log"

docker exec hub-rps-map-stub node -e "fetch('http://127.0.0.1:18080/reset',{method:'POST'}).then(()=>{})" >/dev/null
curl -fsS "${base_url}/internal/api/v1/hubs/${hub_id}" > "${result_dir}/cache-warm-response.json"
cache_key_count="$(docker exec hub-rps-redis redis-cli --scan --pattern 'hub::*' | wc -l | tr -d ' ')"
printf '%s\n' "${cache_key_count}" > "${result_dir}/cache-key-count.txt"
if [[ "${cache_key_count}" -lt 1 ]]; then
  echo "cache warm-up failed" >&2
  exit 1
fi

sleep "${warmup_seconds}"

(
  while true; do
    date -u +%FT%TZ
    docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
      hub-rps-api hub-rps-postgres hub-rps-redis hub-rps-map-stub
    sleep 1
  done
) > "${result_dir}/docker-stats.txt" &
stats_pid=$!

date -u +%FT%TZ > "${result_dir}/read-started-at.txt"
HUB_BASE_URL="${base_url}" \
HUB_ID="${hub_id}" \
HUB_READ_RPS="${read_rps}" \
HUB_READ_DURATION="${read_duration}" \
k6 run --quiet \
  --out "json=${result_dir}/k6-timeseries.json" \
  --summary-export "${result_dir}/k6.json" \
  benchmark/hub-isolation/hub-read.js \
  > "${result_dir}/k6.txt" &
k6_pid=$!

if [[ "${kind}" == "load" ]]; then
  sleep "${trigger_after_seconds}"
  date -u +%FT%TZ > "${result_dir}/route-build-triggered-at.txt"
  docker exec hub-rps-postgres \
    psql -U testuser -d testdb -v ON_ERROR_STOP=1 \
    -c "UPDATE p_hub_route
        SET route_status = 'PENDING',
            next_retry_at = CURRENT_TIMESTAMP,
            processing_token = NULL,
            error_message = NULL,
            distance_km = NULL,
            duration_minutes = NULL,
            resolved_provider = NULL,
            resolved_by_fallback = NULL,
            next_refresh_at = NULL,
            refresh_claimed_at = NULL
        WHERE (start_hub_id = '${hub_id}'::uuid AND end_hub_id IN (
                  SELECT md5('route-end-' || sequence)::uuid
                  FROM generate_series(1, ${pair_count}) AS sequence
               ))
           OR (end_hub_id = '${hub_id}'::uuid AND start_hub_id IN (
                  SELECT md5('route-end-' || sequence)::uuid
                  FROM generate_series(1, ${pair_count}) AS sequence
               ));" \
    > "${result_dir}/route-build-trigger.sql.log"
fi

wait "${k6_pid}"
date -u +%FT%TZ > "${result_dir}/read-finished-at.txt"

route_state="$(docker exec hub-rps-postgres psql -U testuser -d testdb -At -c "SELECT
    COUNT(*) FILTER (WHERE route_status = 'COMPLETE'),
    COUNT(*) FILTER (WHERE route_status = 'PENDING'),
    COUNT(*) FILTER (WHERE route_status = 'PROCESSING'),
    COUNT(*) FILTER (WHERE route_status = 'FAILED')
  FROM p_hub_route
  WHERE (start_hub_id = '${hub_id}'::uuid AND end_hub_id IN (
            SELECT md5('route-end-' || sequence)::uuid
            FROM generate_series(1, ${pair_count}) AS sequence
         ))
     OR (end_hub_id = '${hub_id}'::uuid AND start_hub_id IN (
            SELECT md5('route-end-' || sequence)::uuid
            FROM generate_series(1, ${pair_count}) AS sequence
         ));")"
job_state="$(docker exec hub-rps-postgres psql -U testuser -d testdb -At -c "SELECT status, total_count, remaining_count, failed_count FROM p_hub_route_build_job WHERE hub_id = '${hub_id}'::uuid;")"
printf '%s\n' "${route_state}" > "${result_dir}/route-state.txt"
printf '%s\n' "${job_state}" > "${result_dir}/job-state.txt"
date -u +%FT%TZ > "${result_dir}/route-build-completed-at.txt"

docker exec hub-rps-map-stub node -e "fetch('http://127.0.0.1:18080/stats').then(r=>r.text()).then(t=>console.log(t))" > "${result_dir}/map-stats.json" || true
docker inspect hub-rps-api --format '{{json .HostConfig}}' > "${result_dir}/api-host-config.json"
docker exec hub-rps-api sh -c 'cat /sys/fs/cgroup/memory.events' > "${result_dir}/api-memory-events.txt" || true
docker exec hub-rps-api sh -c 'cat /sys/fs/cgroup/memory.current; grep -E "^(anon|file) " /sys/fs/cgroup/memory.stat' > "${result_dir}/api-memory-stat.txt" || true

kill "${stats_pid}" 2>/dev/null || true
wait "${stats_pid}" 2>/dev/null || true

docker stop hub-rps-api >/dev/null
docker cp hub-rps-api:/results/gc.log "${result_dir}/api-gc.log" >/dev/null 2>&1 || true
docker cp hub-rps-api:/results/hub-read-baseline.jfr "${result_dir}/api.jfr" >/dev/null 2>&1 || true

node benchmark/hub-isolation/summarize-propagation-result.mjs "${result_dir}" > "${result_dir}/summary.md"
printf '%s\n' "${result_dir}"
