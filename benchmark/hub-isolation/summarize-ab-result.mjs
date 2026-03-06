import fs from "node:fs";
import path from "node:path";

const resultDir = process.argv[2];
if (!resultDir) {
  throw new Error("result directory is required");
}

const readText = (name) => {
  const file = path.join(resultDir, name);
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").trim() : "";
};
const readJson = (name) => {
  const text = readText(name);
  return text ? JSON.parse(text) : null;
};
const parseDate = (name) => {
  const text = readText(name);
  return text ? Date.parse(text) : null;
};
const quantile = (values, q) => {
  if (values.length === 0) {
    return null;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.ceil(q * sorted.length) - 1;
  return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
};
const trend = (points) => {
  const values = points.map((point) => point.value);
  return {
    count: values.length,
    p50: quantile(values, 0.5),
    p95: quantile(values, 0.95),
    p99: quantile(values, 0.99),
    max: values.length ? Math.max(...values) : null,
  };
};
const fmt = (value) => value == null ? "-" : `${value.toFixed(2)}ms`;

const summary = readJson("k6.json");
const mapStats = readJson("map-stats.json");
const metadata = Object.fromEntries(
  readText("metadata.txt")
    .split("\n")
    .filter((line) => line.includes("="))
    .map((line) => {
      const index = line.indexOf("=");
      return [line.slice(0, index), line.slice(index + 1)];
    })
);

const points = [];
const timeseriesFile = path.join(resultDir, "k6-timeseries.json");
if (fs.existsSync(timeseriesFile)) {
  for (const line of fs.readFileSync(timeseriesFile, "utf8").split("\n")) {
    if (!line.trim()) {
      continue;
    }
    const event = JSON.parse(line);
    if (event.type === "Point" && event.metric === "http_req_duration") {
      points.push({
        time: Date.parse(event.data.time),
        value: event.data.value,
      });
    }
  }
}

const createStartedAt = parseDate("hub-create-started-at.txt");
const createFinishedAt = parseDate("hub-create-finished-at.txt");
const routeCompletedAt = parseDate("route-build-completed-at.txt");

const phasePoints = {
  full: points,
};
if (createStartedAt != null) {
  phasePoints.pre = points.filter((point) => point.time < createStartedAt);
  phasePoints.common_active_130s = points.filter((point) =>
    point.time >= createStartedAt && point.time < createStartedAt + 130_000
  );
  if (createFinishedAt != null) {
    phasePoints.create_wait = points.filter((point) =>
      point.time >= createStartedAt && point.time <= createFinishedAt
    );
  }
  if (routeCompletedAt != null) {
    phasePoints.route_active = points.filter((point) =>
      point.time >= createStartedAt && point.time <= routeCompletedAt
    );
    phasePoints.after_route_complete = points.filter((point) =>
      point.time > routeCompletedAt
    );
  }
}

const readStartedAt = parseDate("read-started-at.txt");
const readFinishedAt = parseDate("read-finished-at.txt");

const parseGc = (name, startAt = null, endAt = null) => {
  const text = readText(name);
  const pauses = [];
  for (const line of text.split("\n")) {
    if (!line.includes("Pause ")) {
      continue;
    }
    const timeMatch = line.match(/^\[([^\]]+)]/);
    if (timeMatch && (startAt != null || endAt != null)) {
      const eventTime = Date.parse(timeMatch[1]);
      if (startAt != null && eventTime < startAt) {
        continue;
      }
      if (endAt != null && eventTime > endAt) {
        continue;
      }
    }
    const match = line.match(/\s([0-9]+(?:\.[0-9]+)?)(ms|s)$/);
    if (match) {
      pauses.push(Number(match[1]) * (match[2] === "s" ? 1000 : 1));
    }
  }
  return {
    count: pauses.length,
    totalMs: pauses.reduce((sum, value) => sum + value, 0),
    maxMs: pauses.length ? Math.max(...pauses) : 0,
  };
};

const parseCpuMax = (containerName) => {
  const text = readText("docker-stats.txt");
  let max = 0;
  for (const line of text.split("\n")) {
    const parts = line.trim().split(/\s+/);
    if (parts[0] !== containerName) {
      continue;
    }
    const cpu = Number(parts[1]?.replace("%", ""));
    if (!Number.isNaN(cpu)) {
      max = Math.max(max, cpu);
    }
  }
  return max;
};

const phaseSummary = Object.fromEntries(
  Object.entries(phasePoints).map(([name, phase]) => [name, trend(phase)])
);
const apiGc = parseGc("api-gc.log", readStartedAt, readFinishedAt);
const workerGc = parseGc("worker-gc.log", readStartedAt, readFinishedAt);
const metricValues = (name) => summary?.metrics?.[name]?.values ?? summary?.metrics?.[name] ?? {};
const k6Values = metricValues("http_req_duration");
const droppedIterations = metricValues("dropped_iterations")?.count ?? 0;
const checksMetric = metricValues("checks");
const checksRate = checksMetric.rate ?? checksMetric.value;
const createCurl = readText("hub-create-curl.txt");
const outboxCount = readText("outbox-count.txt");

const output = {
  resultDir,
  metadata,
  k6: {
    avg: k6Values.avg,
    med: k6Values.med,
    p95: k6Values["p(95)"],
    p99: k6Values["p(99)"],
    max: k6Values.max,
    droppedIterations,
    checksRate,
  },
  phases: phaseSummary,
  hubCreate: createCurl,
  routeState: readText("route-state.txt"),
  hubStatus: readText("hub-status.txt"),
  jobState: readText("job-state.txt"),
  mapStats,
  outboxCount,
  apiGc,
  workerGc,
  cpuMax: {
    api: parseCpuMax("hub-ab-api"),
    worker: parseCpuMax("hub-ab-route-worker"),
  },
};

fs.writeFileSync(
  path.join(resultDir, "summary.json"),
  JSON.stringify(output, null, 2)
);

console.log(`# ${metadata.mode}-${metadata.kind} run ${metadata.run}`);
console.log("");
console.log(`result_dir: ${resultDir}`);
console.log(`hub_read p95=${fmt(output.k6.p95)} p99=${fmt(output.k6.p99)} max=${fmt(output.k6.max)} dropped=${droppedIterations}`);
if (createCurl) {
  console.log(`hub_create: ${createCurl}`);
}
console.log(`map_calls=${mapStats?.totalDirections ?? "-"} max_map_concurrency=${mapStats?.maxConcurrentDirections ?? "-"}`);
console.log(`route_state=${output.routeState || "-"} hub_status=${output.hubStatus || "-"} job_state=${output.jobState || "-"}`);
console.log(`api_gc_count=${apiGc.count} api_stw_total=${apiGc.totalMs.toFixed(2)}ms api_max_pause=${apiGc.maxMs.toFixed(2)}ms api_cpu_max=${output.cpuMax.api.toFixed(2)}%`);
if (metadata.mode === "B") {
  console.log(`worker_gc_count=${workerGc.count} worker_stw_total=${workerGc.totalMs.toFixed(2)}ms worker_max_pause=${workerGc.maxMs.toFixed(2)}ms worker_cpu_max=${output.cpuMax.worker.toFixed(2)}%`);
}
console.log(`outbox_count=${outboxCount || "-"}`);
console.log("");
console.log("| phase | count | p95 | p99 | max |");
console.log("|---|---:|---:|---:|---:|");
for (const [name, values] of Object.entries(phaseSummary)) {
  console.log(`| ${name} | ${values.count} | ${fmt(values.p95)} | ${fmt(values.p99)} | ${fmt(values.max)} |`);
}
