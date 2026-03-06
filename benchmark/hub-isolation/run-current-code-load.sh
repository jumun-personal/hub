#!/usr/bin/env bash
set -euo pipefail

read_rps="${HUB_READ_RPS:-150}"
read_duration="${HUB_READ_DURATION:-2m}"
warmup_duration="${HUB_READ_WARMUP_DURATION:-2m}"
base_url="${HUB_BASE_URL:-http://localhost:18088}"
hub_id="00000000-0000-0000-0000-000000000001"
new_hub_name="bench-$(date +%H%M%S)"
result_dir="benchmark/hub-isolation/results/current-code-load-$(date +%Y%m%d-%H%M%S)"
compose_file="benchmark/hub-isolation/current-compose.yml"
mkdir -p "${result_dir}"

cleanup() {
  docker compose -f "${compose_file}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

HUB_BENCHMARK_API_HIKARI_POOL_SIZE="${HUB_BENCHMARK_API_HIKARI_POOL_SIZE:-20}" \
HUB_BENCHMARK_WORKER_HIKARI_POOL_SIZE="${HUB_BENCHMARK_WORKER_HIKARI_POOL_SIZE:-20}" \
HUB_ROUTE_BUILD_BATCH_SIZE="${HUB_ROUTE_BUILD_BATCH_SIZE:-10}" \
HUB_ROUTE_BUILD_FIXED_DELAY_MS="${HUB_ROUTE_BUILD_FIXED_DELAY_MS:-500}" \
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

docker exec -i hub-current-postgres \
  psql -U testuser -d testdb -v ON_ERROR_STOP=1 \
  < benchmark/hub-isolation/seed-current-hubs.sql >/dev/null

curl -fsS "${base_url}/internal/api/v1/hubs/${hub_id}" >/dev/null
cache_key_count="$(docker exec hub-current-redis redis-cli --scan --pattern 'hub::*' | wc -l | tr -d ' ')"
if [[ "${cache_key_count}" -lt 1 ]]; then
  echo "cache warm-up failed" >&2
  exit 1
fi

sleep "${warmup_duration}"

(
  while true; do
    docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
      hub-current-api hub-current-route-worker hub-current-kafka hub-current-postgres hub-current-redis
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
date -u +%FT%TZ > "${result_dir}/hub-create-started-at.txt"
create_payload="$(printf '{"centerHubId":null,"name":"%s","address":"benchmark","latitude":37.7,"longitude":127.7,"hubType":"CENTER"}' "${new_hub_name}")"
curl -fsS \
  -H 'Content-Type: application/json' \
  -o "${result_dir}/hub-create-response.json" \
  -w 'http_code=%{http_code} time_total=%{time_total}\n' \
  -d "${create_payload}" \
  "${base_url}/api/v1/hubs" > "${result_dir}/hub-create-curl.txt"
date -u +%FT%TZ > "${result_dir}/hub-create-finished-at.txt"
printf '%s\n' "${new_hub_name}" > "${result_dir}/new-hub-name.txt"

new_hub_id="$(docker exec hub-current-postgres psql -U testuser -d testdb -At -c "SELECT hub_id FROM p_hub WHERE name = '${new_hub_name}';")"
printf '%s\n' "${new_hub_id}" > "${result_dir}/new-hub-id.txt"

wait "${k6_pid}"
date -u +%FT%TZ > "${result_dir}/read-finished-at.txt"

for _ in {1..180}; do
  route_state="$(docker exec hub-current-postgres psql -U testuser -d testdb -At -c "SELECT
      COUNT(*) FILTER (WHERE route_status = 'COMPLETE'),
      COUNT(*) FILTER (WHERE route_status = 'PENDING'),
      COUNT(*) FILTER (WHERE route_status = 'PROCESSING'),
      COUNT(*) FILTER (WHERE route_status = 'FAILED')
    FROM p_hub_route
    WHERE build_hub_id = '${new_hub_id}'::uuid;")"
  hub_status="$(docker exec hub-current-postgres psql -U testuser -d testdb -At -c "SELECT status FROM p_hub WHERE hub_id = '${new_hub_id}'::uuid;")"
  if [[ "${route_state}" == "200|0|0|0" && "${hub_status}" == "COMPLETE" ]]; then
    break
  fi
  sleep 1
done
printf '%s\n' "${route_state}" > "${result_dir}/route-state.txt"
printf '%s\n' "${hub_status}" > "${result_dir}/hub-status.txt"
if [[ "${route_state}" != "200|0|0|0" || "${hub_status}" != "COMPLETE" ]]; then
  echo "current route build did not complete: routes=${route_state}, hub=${hub_status}" >&2
  exit 1
fi
date -u +%FT%TZ > "${result_dir}/route-build-completed-at.txt"

docker exec hub-current-kafka kafka-consumer-groups --bootstrap-server kafka:29092 --all-groups --describe > "${result_dir}/kafka-consumer-groups.txt" 2>&1 || true

kill "${stats_pid}" 2>/dev/null || true
wait "${stats_pid}" 2>/dev/null || true
docker inspect hub-current-api --format '{{json .HostConfig}}' > "${result_dir}/hub-current-api-host-config.json"
docker inspect hub-current-route-worker --format '{{json .HostConfig}}' > "${result_dir}/hub-current-route-worker-host-config.json"
docker exec hub-current-api sh -c 'cat /sys/fs/cgroup/memory.events' > "${result_dir}/api-memory-events.txt"
docker exec hub-current-route-worker sh -c 'cat /sys/fs/cgroup/memory.events' > "${result_dir}/worker-memory-events.txt"
docker exec hub-current-api sh -c 'cat /sys/fs/cgroup/memory.current; grep -E "^(anon|file) " /sys/fs/cgroup/memory.stat' > "${result_dir}/api-memory-stat.txt"
docker exec hub-current-route-worker sh -c 'cat /sys/fs/cgroup/memory.current; grep -E "^(anon|file) " /sys/fs/cgroup/memory.stat' > "${result_dir}/worker-memory-stat.txt"
docker stop hub-current-api hub-current-route-worker >/dev/null
docker cp hub-current-api:/results/current-api-gc.log "${result_dir}/api-gc.log"
docker cp hub-current-route-worker:/results/current-worker-gc.log "${result_dir}/worker-gc.log"
docker cp hub-current-api:/results/current-api.jfr "${result_dir}/api.jfr"
docker cp hub-current-route-worker:/results/current-worker.jfr "${result_dir}/worker.jfr"
printf '%s\n' "${result_dir}"
