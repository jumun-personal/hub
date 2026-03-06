import http from "node:http";

const port = Number(process.env.MAP_STUB_PORT ?? 18080);
const latencyMs = Number(process.env.MAP_STUB_LATENCY_MS ?? 20);
let totalDirections = 0;
let activeDirections = 0;
let maxConcurrentDirections = 0;

const response = JSON.stringify({
  trans_id: "hub-isolation-benchmark",
  routes: [
    {
      result_code: 0,
      result_msg: "success",
      summary: {
        origin: { name: "origin", x: 127.0, y: 37.5 },
        destination: { name: "destination", x: 127.1, y: 37.6 },
        distance: 10000,
        duration: 1200,
        fare: { taxi: 10000, toll: 0 },
      },
    },
  ],
});

http
  .createServer((request, responseStream) => {
    if (request.url === "/stats") {
      const stats = JSON.stringify({
        totalDirections,
        activeDirections,
        maxConcurrentDirections,
      });
      responseStream.writeHead(200, {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(stats),
      });
      responseStream.end(stats);
      return;
    }

    if (request.url === "/reset" && request.method === "POST") {
      totalDirections = 0;
      activeDirections = 0;
      maxConcurrentDirections = 0;
      responseStream.writeHead(204);
      responseStream.end();
      return;
    }

    if (request.url?.startsWith("/v1/directions")) {
      totalDirections += 1;
      activeDirections += 1;
      maxConcurrentDirections = Math.max(maxConcurrentDirections, activeDirections);
      setTimeout(() => {
        responseStream.writeHead(200, {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(response),
        });
        responseStream.end(response);
        activeDirections -= 1;
      }, latencyMs);
      return;
    }

    responseStream.writeHead(404);
    responseStream.end();
  })
  .listen(port, "0.0.0.0", () => {
    process.stdout.write(`map-stub port=${port} latencyMs=${latencyMs}\n`);
  });
