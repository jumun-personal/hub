#!/usr/bin/env node

import { performance } from "node:perf_hooks";

const env = process.env;

const apiKeyInput = env.KAKAO_MOBILITY_API_KEY ?? env.KAKAO_REST_API_KEY;
const authorization = apiKeyInput?.startsWith("KakaoAK ")
  ? apiKeyInput
  : `KakaoAK ${apiKeyInput}`;
const baseUrl = env.KAKAO_MOBILITY_BASE_URL ?? "https://apis-navi.kakaomobility.com";
const endpoint = env.KAKAO_MOBILITY_ENDPOINT ?? "/v1/directions";
const origin = env.KAKAO_ORIGIN ?? "127.10764191124568,37.402464820205246";
const destination = env.KAKAO_DESTINATION ?? "127.11056336672839,37.39419693653072";
const timeoutMs = parsePositiveInt(env.KAKAO_TIMEOUT_MS, 10_000);
const warmup = parseNonNegativeInt(env.KAKAO_WARMUP, 1);
const pauseMs = parsePositiveInt(env.KAKAO_BURST_PAUSE_MS, 1_500);
const levels = parseLevels(env.KAKAO_BURST_LEVELS ?? "1,2,5,10,20");

if (!apiKeyInput) {
  console.error("Missing KAKAO_MOBILITY_API_KEY or KAKAO_REST_API_KEY.");
  console.error("Example:");
  console.error("  KAKAO_REST_API_KEY='...' node benchmark/kakao-rate-limit/kakao-burst-probe.mjs");
  process.exit(2);
}

const requestUrl = new URL(endpoint, baseUrl);
requestUrl.searchParams.set("origin", origin);
requestUrl.searchParams.set("destination", destination);
requestUrl.searchParams.set("priority", "RECOMMEND");
requestUrl.searchParams.set("car_fuel", "GASOLINE");
requestUrl.searchParams.set("car_hipass", "false");
requestUrl.searchParams.set("alternatives", "false");
requestUrl.searchParams.set("road_details", "false");
requestUrl.searchParams.set("summary", env.KAKAO_SUMMARY ?? "true");

console.log(JSON.stringify({
  probe: "kakao-mobility-burst",
  baseUrl,
  endpoint,
  origin,
  destination,
  timeoutMs,
  warmup,
  pauseMs,
  levels,
  authorizationPrefixAdded: !apiKeyInput.startsWith("KakaoAK ")
}));

if (warmup > 0) {
  const warmupResults = await runBurst(warmup, "warmup");
  printSummary("warmup", warmupResults);
  await pause(pauseMs);
}

for (const concurrency of levels) {
  const results = await runBurst(concurrency, `burst-${concurrency}`);
  printSummary(`burst-${concurrency}`, results);
  await pause(pauseMs);
}

async function runBurst(concurrency, label) {
  const startedAt = performance.now();
  const promises = [];
  for (let index = 0; index < concurrency; index += 1) {
    promises.push(callKakao(index, label));
  }
  const results = await Promise.all(promises);
  const elapsedMs = performance.now() - startedAt;
  return results.map((result) => ({ ...result, burstElapsedMs: round(elapsedMs) }));
}

async function callKakao(index, label) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  const startedAt = performance.now();
  try {
    const response = await fetch(requestUrl, {
      method: "GET",
      headers: {
        Authorization: authorization,
        "Content-Type": "application/json",
        Accept: "application/json"
      },
      signal: controller.signal
    });
    const latencyMs = performance.now() - startedAt;
    const bodyText = await response.text();
    const body = safeJson(bodyText);
    return {
      label,
      index,
      ok: response.ok,
      status: response.status,
      statusText: response.statusText,
      latencyMs: round(latencyMs),
      retryAfter: response.headers.get("retry-after"),
      rateLimitLimit: response.headers.get("ratelimit-limit") ?? response.headers.get("x-ratelimit-limit"),
      rateLimitRemaining: response.headers.get("ratelimit-remaining") ?? response.headers.get("x-ratelimit-remaining"),
      rateLimitReset: response.headers.get("ratelimit-reset") ?? response.headers.get("x-ratelimit-reset"),
      kakaoCode: body?.code ?? null,
      kakaoMessage: body?.msg ?? body?.message ?? null,
      routeResultCode: body?.routes?.[0]?.result_code ?? null,
      routeResultMsg: body?.routes?.[0]?.result_msg ?? null,
      transIdPresent: typeof body?.trans_id === "string" && body.trans_id.length > 0,
      bodyBytes: Buffer.byteLength(bodyText)
    };
  } catch (error) {
    const latencyMs = performance.now() - startedAt;
    return {
      label,
      index,
      ok: false,
      status: "CLIENT_ERROR",
      statusText: error?.name ?? "Error",
      latencyMs: round(latencyMs),
      retryAfter: null,
      rateLimitLimit: null,
      rateLimitRemaining: null,
      rateLimitReset: null,
      kakaoCode: null,
      kakaoMessage: error?.message ?? String(error),
      routeResultCode: null,
      routeResultMsg: null,
      transIdPresent: false,
      bodyBytes: 0
    };
  } finally {
    clearTimeout(timeout);
  }
}

function printSummary(label, results) {
  const latencies = results.map((result) => result.latencyMs).sort((a, b) => a - b);
  const statusCounts = countBy(results, (result) => String(result.status));
  const kakaoCodeCounts = countBy(
    results.filter((result) => result.kakaoCode !== null),
    (result) => String(result.kakaoCode)
  );
  const retryAfterValues = [...new Set(results.map((result) => result.retryAfter).filter(Boolean))];
  const rateLimitHeaders = results
    .map((result) => ({
      limit: result.rateLimitLimit,
      remaining: result.rateLimitRemaining,
      reset: result.rateLimitReset
    }))
    .filter((headers) => headers.limit || headers.remaining || headers.reset);

  const summary = {
    label,
    requests: results.length,
    statusCounts,
    kakaoCodeCounts,
    retryAfterValues,
    latencyMs: {
      min: percentile(latencies, 0),
      p50: percentile(latencies, 50),
      p95: percentile(latencies, 95),
      p99: percentile(latencies, 99),
      max: percentile(latencies, 100)
    },
    elapsedMs: results[0]?.burstElapsedMs ?? 0,
    rateLimitHeaders: dedupeObjects(rateLimitHeaders),
    failures: results
      .filter((result) => !result.ok || result.kakaoCode !== null)
      .map((result) => ({
        index: result.index,
        status: result.status,
        statusText: result.statusText,
        kakaoCode: result.kakaoCode,
        kakaoMessage: result.kakaoMessage,
        retryAfter: result.retryAfter,
        latencyMs: result.latencyMs
      }))
  };

  console.log(JSON.stringify(summary));
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function countBy(values, mapper) {
  return values.reduce((acc, value) => {
    const key = mapper(value);
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});
}

function percentile(sorted, pct) {
  if (sorted.length === 0) {
    return 0;
  }
  if (pct <= 0) {
    return round(sorted[0]);
  }
  if (pct >= 100) {
    return round(sorted[sorted.length - 1]);
  }
  const index = Math.ceil((pct / 100) * sorted.length) - 1;
  return round(sorted[Math.max(0, Math.min(sorted.length - 1, index))]);
}

function parsePositiveInt(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function parseNonNegativeInt(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function parseLevels(value) {
  const parsed = value
    .split(",")
    .map((item) => Number.parseInt(item.trim(), 10))
    .filter((item) => Number.isFinite(item) && item > 0);
  return parsed.length > 0 ? parsed : [1, 2, 5, 10, 20];
}

function dedupeObjects(values) {
  const seen = new Set();
  return values.filter((value) => {
    const key = JSON.stringify(value);
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function round(value) {
  return Math.round(value * 100) / 100;
}

function pause(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
