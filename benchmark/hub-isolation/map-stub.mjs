import http from "node:http";

const port = Number(process.env.MAP_STUB_PORT ?? 18080);
const latencyMs = Number(process.env.MAP_STUB_LATENCY_MS ?? 20);

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
    if (request.url?.startsWith("/v1/directions")) {
      setTimeout(() => {
        responseStream.writeHead(200, {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(response),
        });
        responseStream.end(response);
      }, latencyMs);
      return;
    }

    responseStream.writeHead(404);
    responseStream.end();
  })
  .listen(port, "0.0.0.0", () => {
    process.stdout.write(`map-stub port=${port} latencyMs=${latencyMs}\n`);
  });
