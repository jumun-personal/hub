package com.jumunhasyeo.hub.application;

import com.jumunhasyeo.common.scheduler.HubRouteBuildScheduler;
import com.jumunhasyeo.common.scheduler.HubRouteRefreshScheduler;
import com.jumunhasyeo.common.scheduler.InboxPollingScheduler;
import com.jumunhasyeo.common.scheduler.OutboxPollingScheduler;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class HubCacheLoadTest extends IntegrationTest {

    private static final int COLD_MISS_REQUESTS = 50;

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

    @MockitoBean
    private OutboxPollingScheduler outboxPollingScheduler;

    @MockitoBean
    private InboxPollingScheduler inboxPollingScheduler;

    @MockitoBean
    private HubRouteBuildScheduler hubRouteBuildScheduler;

    @MockitoBean
    private HubRouteRefreshScheduler hubRouteRefreshScheduler;

    @BeforeEach
    @Override
    protected void truncateTables() {
        super.truncateTables();
        clearCaches();
    }

    @Test
    @DisplayName("캐시 무효화 직후 동일 허브 동시 조회는 DB 조회 한 번으로 병합된다")
    void coldMissSingleFlightMergesConcurrentDbLoads() throws Exception {
        // given
        UUID hubId = createHub();
        clearCaches();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        // when
        LoadResult result = runConcurrentColdMiss(client, hubId, statistics);

        // then
        System.out.printf("%n=== Hub single-flight cold-miss result ===%n%s%n", result);
        assertThat(result.successes()).isEqualTo(COLD_MISS_REQUESTS);
        assertThat(result.preparedStatements()).isEqualTo(1);
        assertThat(redisTemplate.hasKey("hub::" + hubId)).isTrue();
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

    private LoadResult runConcurrentColdMiss(
            HttpClient client,
            UUID hubId,
            Statistics statistics
    ) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(COLD_MISS_REQUESTS);
        CountDownLatch ready = new CountDownLatch(COLD_MISS_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RequestResult>> futures = new ArrayList<>(COLD_MISS_REQUESTS);

        try {
            for (int i = 0; i < COLD_MISS_REQUESTS; i++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        return new RequestResult(false, 0);
                    }
                    return sendGetHubRequest(client, hubId);
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();
            start.countDown();

            List<RequestResult> requestResults = new ArrayList<>(COLD_MISS_REQUESTS);
            for (Future<RequestResult> future : futures) {
                requestResults.add(future.get(10, TimeUnit.SECONDS));
            }
            long elapsedNanos = System.nanoTime() - started;

            List<Long> latencies = requestResults.stream()
                    .filter(RequestResult::success)
                    .map(RequestResult::elapsedNanos)
                    .sorted()
                    .toList();

            return LoadResult.from(
                    "REDIS_COLD_SINGLE_FLIGHT",
                    requestResults.size(),
                    latencies,
                    elapsedNanos,
                    statistics.getPrepareStatementCount()
            );
        } finally {
            start.countDown();
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
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
