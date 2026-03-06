#!/usr/bin/env bash
set -euo pipefail

read_rps="${HUB_READ_RPS:-100}"
read_duration="${HUB_READ_DURATION:-2m}"
warmup_duration="${HUB_READ_WARMUP_DURATION:-2m}"
base_url="${HUB_BASE_URL:-http://localhost:18088}"
hub_id="00000000-0000-0000-0000-000000000001"
pair_count="${ROUTE_BUILD_PAIR_COUNT:-100}"
result_dir="benchmark/hub-isolation/results/route-build-load-$(date +%Y%m%d-%H%M%S)"
compose_file="benchmark/hub-isolation/rps-baseline-compose.yml"
mkdir -p "${result_dir}"

cleanup() {
  docker compose -f "${compose_file}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

HUB_ROUTE_BUILD_SCHEDULER_ENABLED=true \
HUB_ROUTE_BUILD_BATCH_SIZE=10 \
HUB_ROUTE_BUILD_FIXED_DELAY_MS=500 \
HUB_BENCHMARK_HIKARI_POOL_SIZE="${HUB_BENCHMARK_HIKARI_POOL_SIZE:-10}" \
docker compose -f "${compose_file}" up -d --wait

for _ in {1..120}; do
  http_status="$(curl --max-time 2 -s -o /dev/null -w '%{http_code}' "${base_url}/actuator/health" || true)"
  if [[ "${http_status}" != "000" ]]; then
    break
  fi
  sleep 1
done
if [[ "${http_status}" == "000" ]]; then
  echo "hub-api did not open its HTTP port" >&2
  exit 1
fi

docker exec -i hub-rps-postgres \
  psql -U testuser -d testdb -v ON_ERROR_STOP=1 \
  < benchmark/hub-isolation/seed-routes.sql >/dev/null

curl -fsS "${base_url}/internal/api/v1/hubs/${hub_id}" >/dev/null
cache_key_count="$(docker exec hub-rps-redis redis-cli --scan --pattern 'hub::*' | wc -l | tr -d ' ')"
if [[ "${cache_key_count}" -lt 1 ]]; then
  echo "cache warm-up failed" >&2
  exit 1
fi

sleep "${warmup_duration}"

(
  while true; do
    docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' hub-rps-api
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
  --summary-export "${result_dir}/k6.json" \
  benchmark/hub-isolation/hub-read.js > "${result_dir}/k6.txt" &
k6_pid=$!

sleep 1
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
             ));" >/dev/null

wait "${k6_pid}"
date -u +%FT%TZ > "${result_dir}/read-finished-at.txt"

for _ in {1..90}; do
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
  if [[ "${route_state}" == "$((pair_count * 2))|0|0|0" ]]; then
    break
  fi
  sleep 1
done
printf '%s\n' "${route_state}" > "${result_dir}/route-state.txt"
if [[ "${route_state}" != "$((pair_count * 2))|0|0|0" ]]; then
  echo "route build did not complete: ${route_state}" >&2
  exit 1
fi
date -u +%FT%TZ > "${result_dir}/route-build-completed-at.txt"

kill "${stats_pid}" 2>/dev/null || true
wait "${stats_pid}" 2>/dev/null || true
docker inspect hub-rps-api --format '{{json .HostConfig}}' > "${result_dir}/hub-rps-api-host-config.json"
docker exec hub-rps-api sh -c 'cat /sys/fs/cgroup/memory.events' > "${result_dir}/memory-events.txt"
docker exec hub-rps-api sh -c 'cat /sys/fs/cgroup/memory.current; grep -E "^(anon|file) " /sys/fs/cgroup/memory.stat' > "${result_dir}/memory-stat.txt"
docker stop hub-rps-api >/dev/null
docker cp hub-rps-api:/results/gc.log "${result_dir}/gc.log"
docker cp hub-rps-api:/results/hub-read-baseline.jfr "${result_dir}/hub-read-baseline.jfr"
printf '%s\n' "${result_dir}"
