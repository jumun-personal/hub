#!/usr/bin/env bash
set -euo pipefail

case_name="${1:?case name is required}"
run_number="${2:?run number is required}"
hub_base_url="${3:?Hub API base URL is required}"
resource_containers="${4:?comma-separated app container names are required}"

result_dir="benchmark/hub-isolation/results"
summary_file="${result_dir}/${case_name}-run${run_number}.json"
stats_file="${result_dir}/${case_name}-run${run_number}-stats.txt"

mkdir -p "${result_dir}"

docker exec -i hub-isolation-postgres \
  psql -U hubbench -d hubbench -v ON_ERROR_STOP=1 \
  < benchmark/hub-isolation/seed-routes.sql \
  >/dev/null

HUB_BASE_URL="${hub_base_url}" \
HUB_ID="00000000-0000-0000-0000-000000000001" \
HUB_READ_RPS=100 \
HUB_READ_DURATION=20s \
k6 run --quiet \
  --summary-export "${summary_file}" \
  benchmark/hub-isolation/hub-read.js &
k6_pid=$!

sleep 1

docker exec hub-isolation-postgres \
  psql -U hubbench -d hubbench \
  -c "UPDATE p_hub_route
      SET next_refresh_at = CURRENT_TIMESTAMP - INTERVAL '1 second',
          refresh_claimed_at = NULL" \
  >/dev/null

IFS=',' read -r -a containers <<< "${resource_containers}"
for sample in {1..8}; do
  docker stats --no-stream \
    --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
    "${containers[@]}"
done > "${stats_file}"

wait "${k6_pid}"

route_state="$(
  docker exec hub-isolation-postgres \
    psql -U hubbench -d hubbench -At \
    -c "SELECT
          COUNT(*) FILTER (WHERE next_refresh_at > CURRENT_TIMESTAMP),
          COUNT(*) FILTER (WHERE next_refresh_at <= CURRENT_TIMESTAMP),
          COUNT(*) FILTER (WHERE refresh_claimed_at IS NOT NULL),
          COUNT(*) FILTER (WHERE error_message IS NOT NULL)
        FROM p_hub_route"
)"

if [[ "${route_state}" != "540|0|0|0" ]]; then
  echo "invalid route state: ${route_state}" >&2
  exit 1
fi

echo "${case_name} run=${run_number} route_state=${route_state}"
