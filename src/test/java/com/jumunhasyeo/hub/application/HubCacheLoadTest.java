package com.jumunhasyeo.hub.application;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class HubCacheLoadTest extends IntegrationTest {

    private static final int REQUESTS = 3_000;
    private static final int CONCURRENCY = 50;
    private static final int WARMUP_REQUESTS = 200;

    @LocalServerPort
    private int port;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    protected void truncateTables() {
        clearCaches();
    }

    @Test
    @DisplayName("허브 단건 조회 Cache-Aside 적용 전후 부하 테스트")
    void compareHubFindByIdLoadWithAndWithoutRedisCache() throws Exception {
        UUID hubId = createHub();

        LoadResult withoutCache = runScenario("NONE", hubId);
        LoadResult withRedisCache = runScenario("REDIS", hubId);

        double averageImprovement = improvement(withoutCache.averageMs(), withRedisCache.averageMs());
        double p95Improvement = improvement(withoutCache.p95Ms(), withRedisCache.p95Ms());
        double throughputImprovement = throughputImprovement(withoutCache.requestsPerSecond(), withRedisCache.requestsPerSecond());
        double dbStatementReduction = improvement(withoutCache.preparedStatements(), withRedisCache.preparedStatements());

        System.out.printf("%n=== Hub cache load test result ===%n");
        System.out.println(withoutCache);
        System.out.println(withRedisCache);
        System.out.printf(
                "improvement average=%.2f%% p95=%.2f%% throughput=%.2f%% dbPreparedStatements=%.2f%%%n",
                averageImprovement,
                p95Improvement,
                throughputImprovement,
                dbStatementReduction
        );

        assertThat(withoutCache.successes()).isEqualTo(REQUESTS);
        assertThat(withRedisCache.successes()).isEqualTo(REQUESTS);
        assertThat(withRedisCache.preparedStatements()).isLessThan(withoutCache.preparedStatements());
    }

    private UUID createHub() {
        return transactionTemplate.execute(status -> {
            Hub hub = Hub.createBranchHub(
                    "부하테스트허브",
                    Address.of("서울시 송파구", Coordinate.of(37.5, 127.0))
            );
            hub.activate();
            entityManager.persist(hub);
            entityManager.flush();
            entityManager.clear();
            return hub.getHubId();
        });
    }

    private LoadResult runScenario(String cacheType, UUID hubId) throws Exception {
        switchCache(cacheType);
        clearCaches();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            sendGetHubRequest(client, hubId);
        }

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENCY);
        List<Callable<RequestResult>> tasks = new ArrayList<>(REQUESTS);
        for (int i = 0; i < REQUESTS; i++) {
            tasks.add(() -> sendGetHubRequest(client, hubId));
        }

        long started = System.nanoTime();
        List<RequestResult> requestResults = executorService.invokeAll(tasks)
                .stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        return new RequestResult(false, 0);
                    }
                })
                .toList();
        long elapsedNanos = System.nanoTime() - started;

        executorService.shutdown();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        List<Long> latencies = requestResults.stream()
                .filter(RequestResult::success)
                .map(RequestResult::elapsedNanos)
                .sorted()
                .toList();

        return LoadResult.from(
                cacheType,
                requestResults.size(),
                latencies,
                elapsedNanos,
                statistics.getPrepareStatementCount()
        );
    }

    private void switchCache(String cacheType) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/internal/api/v1/dynamic/hub?type=" + cacheType))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(3))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private RequestResult sendGetHubRequest(HttpClient client, UUID hubId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/internal/api/v1/hubs/" + hubId))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        long started = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsedNanos = System.nanoTime() - started;

        boolean success = response.statusCode() == 200 && response.body().contains(hubId.toString());
        return new RequestResult(success, elapsedNanos);
    }

    private void clearCaches() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });

        Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()
                .serverCommands()
                .flushAll();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private double improvement(double before, double after) {
        if (before == 0) {
            return 0;
        }
        return ((before - after) / before) * 100.0;
    }

    private double throughputImprovement(double before, double after) {
        if (before == 0) {
            return 0;
        }
        return ((after - before) / before) * 100.0;
    }

    private record RequestResult(boolean success, long elapsedNanos) {
    }

    private record LoadResult(
            String cacheType,
            int totalRequests,
            int successes,
            double averageMs,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double requestsPerSecond,
            long preparedStatements
    ) {
        private static LoadResult from(
                String cacheType,
                int totalRequests,
                List<Long> sortedLatencies,
                long elapsedNanos,
                long preparedStatements
        ) {
            int successes = sortedLatencies.size();
            double averageMs = sortedLatencies.stream()
                    .mapToDouble(LoadResult::nanosToMillis)
                    .average()
                    .orElse(0);

            return new LoadResult(
                    cacheType,
                    totalRequests,
                    successes,
                    averageMs,
                    percentile(sortedLatencies, 0.50),
                    percentile(sortedLatencies, 0.95),
                    percentile(sortedLatencies, 0.99),
                    totalRequests / (elapsedNanos / 1_000_000_000.0),
                    preparedStatements
            );
        }

        private static double percentile(List<Long> sortedLatencies, double percentile) {
            if (sortedLatencies.isEmpty()) {
                return 0;
            }

            int index = (int) Math.ceil(percentile * sortedLatencies.size()) - 1;
            int boundedIndex = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
            return nanosToMillis(sortedLatencies.get(boundedIndex));
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "cache=%s requests=%d success=%d avgMs=%.3f p50Ms=%.3f p95Ms=%.3f p99Ms=%.3f rps=%.2f dbPreparedStatements=%d",
                    cacheType,
                    totalRequests,
                    successes,
                    averageMs,
                    p50Ms,
                    p95Ms,
                    p99Ms,
                    requestsPerSecond,
                    preparedStatements
            );
        }
    }

}
