#!/usr/bin/env bash
set -euo pipefail

timestamp="$(date +%Y%m%d-%H%M%S)"
result_dir="${KAKAO_BURST_RESULT_DIR:-benchmark/kakao-rate-limit/results/kakao-burst-${timestamp}}"
mkdir -p "${result_dir}"

if [[ -z "${KAKAO_MOBILITY_API_KEY:-}" && -z "${KAKAO_REST_API_KEY:-}" ]]; then
  echo "Missing KAKAO_MOBILITY_API_KEY or KAKAO_REST_API_KEY." >&2
  echo "Example:" >&2
  echo "  KAKAO_REST_API_KEY='...' KAKAO_BURST_LEVELS='1,2,5,10,20' bash benchmark/kakao-rate-limit/run-kakao-burst-probe.sh" >&2
  exit 2
fi

echo "result_dir=${result_dir}"
echo "levels=${KAKAO_BURST_LEVELS:-1,2,5,10,20}"
echo "warmup=${KAKAO_WARMUP:-1}"
echo "pause_ms=${KAKAO_BURST_PAUSE_MS:-1500}"
echo "timeout_ms=${KAKAO_TIMEOUT_MS:-10000}"

node benchmark/kakao-rate-limit/kakao-burst-probe.mjs \
  | tee "${result_dir}/summary.jsonl"
