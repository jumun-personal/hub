#!/usr/bin/env bash
set -euo pipefail

mode="${1:?A or B is required}"
kind="${2:?idle or load is required}"
run_number="${3:-1}"

if [[ "${mode}" != "A" && "${mode}" != "B" ]]; then
  echo "mode must be A or B" >&2
  exit 2
fi
if [[ "${kind}" != "idle" && "${kind}" != "load" ]]; then
  echo "kind must be idle or load" >&2
  exit 2
fi

read_rps="${HUB_AB_READ_RPS:-150}"
read_duration="${HUB_AB_READ_DURATION:-300s}"
warmup_seconds="${HUB_AB_WARMUP_SECONDS:-120}"
trigger_after_seconds="${HUB_AB_TRIGGER_AFTER_SECONDS:-30}"
route_wait_seconds="${HUB_AB_ROUTE_WAIT_SECONDS:-360}"
base_url="${HUB_AB_BASE_URL:-http://localhost:18088}"
map_stub_url="${HUB_AB_MAP_STUB_URL:-http://localhost:18089}"
hub_id="00000000-0000-0000-0000-000000000001"
timestamp="$(date +%Y%m%d-%H%M%S)"
lower_mode="$(printf '%s' "${mode}" | tr '[:upper:]' '[:lower:]')"
result_dir="benchmark/hub-isolation/results/ab-${lower_mode}-${kind}-run${run_number}-${timestamp}"
compose_file="benchmark/hub-isolation/ab-compose.yml"
kind_prefix="$(printf '%s' "${kind}" | cut -c 1)"
timestamp_suffix="${timestamp##*-}"
new_hub_name="ab-${lower_mode}-${kind_prefix}-${run_number}-${timestamp_suffix}"

mkdir -p "${result_dir}"

compose=(docker compose -f "${compose_file}")
if [[ "${mode}" == "B" ]]; then
  compose+=(--profile worker)
fi

cleanup() {
  docker compose -f "${compose_file}" --profile worker down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

cleanup

{
  echo "mode=${mode}"
  echo "kind=${kind}"
  echo "run=${run_number}"
  echo "read_rps=${read_rps}"
  echo "read_duration=${read_duration}"
  echo "warmup_seconds=${warmup_seconds}"
  echo "trigger_after_seconds=${trigger_after_seconds}"
  echo "api_hikari=${HUB_AB_API_HIKARI_POOL_SIZE:-20}"
  echo "worker_hikari=${HUB_AB_WORKER_HIKARI_POOL_SIZE:-4}"
  echo "heap_xms=${HUB_AB_XMS:--Xms256m}"
  echo "heap_xmx=${HUB_AB_XMX:--Xmx256m}"
  echo "gc=${HUB_AB_GC_OPTION:--XX:+UseG1GC}"
  git rev-parse HEAD
  shasum -a 256 build/libs/hub_prodcut_stock_company-0.0.1-SNAPSHOT.jar
} > "${result_dir}/metadata.txt"

HUB_AB_SYNC_ROUTE_BUILD_ENABLED="$([[ "${mode}" == "A" ]] && echo true || echo false)" \
HUB_AB_API_HIKARI_POOL_SIZE="${HUB_AB_API_HIKARI_POOL_SIZE:-20}" \
HUB_AB_WORKER_HIKARI_POOL_SIZE="${HUB_AB_WORKER_HIKARI_POOL_SIZE:-4}" \
HUB_AB_ROUTE_BUILD_BATCH_SIZE="${HUB_AB_ROUTE_BUILD_BATCH_SIZE:-2}" \
HUB_AB_ROUTE_BUILD_FIXED_DELAY_MS="${HUB_AB_ROUTE_BUILD_FIXED_DELAY_MS:-500}" \
HUB_AB_XMS="${HUB_AB_XMS:--Xms256m}" \
HUB_AB_XMX="${HUB_AB_XMX:--Xmx256m}" \
HUB_AB_GC_OPTION="${HUB_AB_GC_OPTION:--XX:+UseG1GC}" \
"${compose[@]}" up -d

for _ in {1..180}; do
  http_status="$(curl --max-time 2 -s -o /dev/null -w '%{http_code}' "${base_url}/actuator/health" || true)"
  if [[ "${http_status}" != "000" ]]; then
    break
  fi
  sleep 1
done
if [[ "${http_status}" == "000" ]]; then
  docker logs hub-ab-api > "${result_dir}/api-startup.log" 2>&1 || true
  echo "hub-api did not open its HTTP port" >&2
  exit 1
fi

docker exec -i hub-ab-postgres \
  psql -U testuser -d testdb -v ON_ERROR_STOP=1 \
  < benchmark/hub-isolation/seed-current-hubs.sql \
  > "${result_dir}/seed.log"

curl -fsS -X POST "${map_stub_url}/reset" >/dev/null
curl -fsS "${base_url}/internal/api/v1/hubs/${hub_id}" > "${result_dir}/cache-warm-response.json"
cache_key_count="$(docker exec hub-ab-redis redis-cli --scan --pattern 'hub::*' | wc -l | tr -d ' ')"
printf '%s\n' "${cache_key_count}" > "${result_dir}/cache-key-count.txt"
if [[ "${cache_key_count}" -lt 1 ]]; then
  echo "cache warm-up failed" >&2
  exit 1
fi

sleep "${warmup_seconds}"

containers=(hub-ab-api hub-ab-postgres hub-ab-redis hub-ab-map-stub)
if [[ "${mode}" == "B" ]]; then
  containers+=(hub-ab-route-worker)
fi

(
  while true; do
    date -u +%FT%TZ
    docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' "${containers[@]}"
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

new_hub_id=""
if [[ "${kind}" == "load" ]]; then
  sleep "${trigger_after_seconds}"
  date -u +%FT%TZ > "${result_dir}/hub-create-started-at.txt"
  create_payload="$(printf '{"centerHubId":null,"name":"%s","address":"benchmark","latitude":37.7,"longitude":127.7,"hubType":"CENTER"}' "${new_hub_name}")"
  curl -fsS \
    -H 'Content-Type: application/json' \
    -o "${result_dir}/hub-create-response.json" \
    -w 'http_code=%{http_code} time_total=%{time_total}\n' \
    -d "${create_payload}" \
    "${base_url}/api/v1/hubs" \
    > "${result_dir}/hub-create-curl.txt"
  date -u +%FT%TZ > "${result_dir}/hub-create-finished-at.txt"
  printf '%s\n' "${new_hub_name}" > "${result_dir}/new-hub-name.txt"

  new_hub_id="$(docker exec hub-ab-postgres psql -U testuser -d testdb -At -c "SELECT hub_id FROM p_hub WHERE name = '${new_hub_name}';")"
  printf '%s\n' "${new_hub_id}" > "${result_dir}/new-hub-id.txt"

  for _ in $(seq 1 "${route_wait_seconds}"); do
    hub_status="$(docker exec hub-ab-postgres psql -U testuser -d testdb -At -c "SELECT status FROM p_hub WHERE hub_id = '${new_hub_id}'::uuid;")"
    if [[ "${mode}" == "A" ]]; then
      route_state="$(docker exec hub-ab-postgres psql -U testuser -d testdb -At -c "SELECT
          COUNT(*) FILTER (WHERE route_status = 'COMPLETE'),
          COUNT(*)
        FROM p_hub_route
        WHERE is_deleted = false
          AND (start_hub_id = '${new_hub_id}'::uuid OR end_hub_id = '${new_hub_id}'::uuid);")"
      job_state="not-applicable"
      if [[ "${route_state}" == "200|200" && "${hub_status}" == "COMPLETE" ]]; then
        break
      fi
    else
      route_state="$(docker exec hub-ab-postgres psql -U testuser -d testdb -At -c "SELECT
          COUNT(*) FILTER (WHERE route_status = 'COMPLETE'),
          COUNT(*) FILTER (WHERE route_status = 'PENDING'),
          COUNT(*) FILTER (WHERE route_status = 'PROCESSING'),
          COUNT(*) FILTER (WHERE route_status = 'FAILED')
        FROM p_hub_route
        WHERE build_hub_id = '${new_hub_id}'::uuid;")"
      job_state="$(docker exec hub-ab-postgres psql -U testuser -d testdb -At -c "SELECT status, total_count, remaining_count, failed_count FROM p_hub_route_build_job WHERE hub_id = '${new_hub_id}'::uuid;")"
      if [[ "${route_state}" == "200|0|0|0" && "${hub_status}" == "COMPLETE" && "${job_state}" == "COMPLETE|100|0|0" ]]; then
        break
      fi
    fi
    sleep 1
  done
  printf '%s\n' "${route_state}" > "${result_dir}/route-state.txt"
  printf '%s\n' "${hub_status}" > "${result_dir}/hub-status.txt"
  printf '%s\n' "${job_state}" > "${result_dir}/job-state.txt"
  date -u +%FT%TZ > "${result_dir}/route-build-completed-at.txt"
fi

wait "${k6_pid}"
date -u +%FT%TZ > "${result_dir}/read-finished-at.txt"

curl -fsS "${map_stub_url}/stats" > "${result_dir}/map-stats.json" || true
docker compose -f "${compose_file}" config --services > "${result_dir}/compose-services.txt"
docker inspect hub-ab-api --format '{{json .HostConfig}}' > "${result_dir}/api-host-config.json"
docker exec hub-ab-api sh -c 'cat /sys/fs/cgroup/memory.events' > "${result_dir}/api-memory-events.txt" || true
docker exec hub-ab-api sh -c 'cat /sys/fs/cgroup/memory.current; grep -E "^(anon|file) " /sys/fs/cgroup/memory.stat' > "${result_dir}/api-memory-stat.txt" || true
docker exec hub-ab-postgres psql -U testuser -d testdb -At -c "SELECT COUNT(*) FROM p_outbox_events;" > "${result_dir}/outbox-count.txt" || true

if [[ "${mode}" == "B" ]]; then
  docker inspect hub-ab-route-worker --format '{{json .HostConfig}}' > "${result_dir}/worker-host-config.json"
  docker exec hub-ab-route-worker sh -c 'cat /sys/fs/cgroup/memory.events' > "${result_dir}/worker-memory-events.txt" || true
  docker exec hub-ab-route-worker sh -c 'cat /sys/fs/cgroup/memory.current; grep -E "^(anon|file) " /sys/fs/cgroup/memory.stat' > "${result_dir}/worker-memory-stat.txt" || true
fi

kill "${stats_pid}" 2>/dev/null || true
wait "${stats_pid}" 2>/dev/null || true

if [[ "${mode}" == "B" ]]; then
  docker stop hub-ab-api hub-ab-route-worker >/dev/null
else
  docker stop hub-ab-api >/dev/null
fi
docker cp hub-ab-api:/results/ab-api-gc.log "${result_dir}/api-gc.log" >/dev/null 2>&1 || true
docker cp hub-ab-api:/results/ab-api.jfr "${result_dir}/api.jfr" >/dev/null 2>&1 || true
if [[ "${mode}" == "B" ]]; then
  docker cp hub-ab-route-worker:/results/ab-worker-gc.log "${result_dir}/worker-gc.log" >/dev/null 2>&1 || true
  docker cp hub-ab-route-worker:/results/ab-worker.jfr "${result_dir}/worker.jfr" >/dev/null 2>&1 || true
fi

node benchmark/hub-isolation/summarize-ab-result.mjs "${result_dir}" > "${result_dir}/summary.md"
printf '%s\n' "${result_dir}"
