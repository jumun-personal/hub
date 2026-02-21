import http from "k6/http";
import { check } from "k6";

export const options = {
  scenarios: {
    hub_read: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.HUB_READ_RPS || 100),
      timeUnit: "1s",
      duration: __ENV.HUB_READ_DURATION || "15s",
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
  },
  summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
};

export default function () {
  const response = http.get(
    `${__ENV.HUB_BASE_URL}/internal/api/v1/hubs/${__ENV.HUB_ID}`,
  );
  check(response, { "hub read is 200": (result) => result.status === 200 });
}
