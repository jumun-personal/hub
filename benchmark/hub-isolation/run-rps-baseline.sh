#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -eq 0 ]]; then
  echo "usage: $0 <rps> [rps ...]" >&2
  exit 2
fi

duration="${HUB_READ_DURATION:-2m}"
warmup_duration="${HUB_READ_WARMUP_DURATION:-2m}"
base_url="${HUB_BASE_URL:-http://localhost:18088}"
hub_id="00000000-0000-0000-0000-000000000001"
result_dir="benchmark/hub-isolation/results/rps-baseline-$(date +%Y%m%d-%H%M%S)"
compose_file="benchmark/hub-isolation/rps-baseline-compose.yml"
mkdir -p "${result_dir}"

cleanup() {
  docker compose -f "${compose_file}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

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

# Spring context, class loading, JIT warm-up은 부하 구간에서 제외한다.
sleep "${warmup_duration}"

for rps in "$@"; do
  run_prefix="${result_dir}/${rps}rps"
  date -u +%FT%TZ > "${run_prefix}-started-at.txt"

  (
    while true; do
      docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' hub-rps-api
      sleep 1
    done
  ) > "${run_prefix}-docker-stats.txt" &
  stats_pid=$!

  HUB_BASE_URL="${base_url}" \
  HUB_ID="${hub_id}" \
  HUB_READ_RPS="${rps}" \
  HUB_READ_DURATION="${duration}" \
  k6 run --quiet \
    --summary-export "${run_prefix}-k6.json" \
    benchmark/hub-isolation/hub-read.js > "${run_prefix}-k6.txt"

  kill "${stats_pid}" 2>/dev/null || true
  wait "${stats_pid}" 2>/dev/null || true
  date -u +%FT%TZ > "${run_prefix}-finished-at.txt"
  sleep 20
done

docker inspect hub-rps-api --format '{{json .HostConfig}}' > "${result_dir}/hub-rps-api-host-config.json"
docker exec hub-rps-api sh -c 'cat /sys/fs/cgroup/memory.events' > "${result_dir}/memory-events.txt"
docker exec hub-rps-api sh -c 'cat /sys/fs/cgroup/memory.current; grep -E "^(anon|file) " /sys/fs/cgroup/memory.stat' > "${result_dir}/memory-stat.txt"
docker stop hub-rps-api >/dev/null
docker cp hub-rps-api:/results/gc.log "${result_dir}/gc.log"
docker cp hub-rps-api:/results/hub-read-baseline.jfr "${result_dir}/hub-read-baseline.jfr"
printf '%s\n' "${result_dir}"
