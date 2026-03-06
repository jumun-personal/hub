package com.jumunhasyeo.hub.application;

import com.jumunhasyeo.common.scheduler.HubRouteBuildScheduler;
import com.jumunhasyeo.common.scheduler.HubRouteRefreshScheduler;
import com.jumunhasyeo.common.scheduler.InboxPollingScheduler;
import com.jumunhasyeo.common.scheduler.OutboxPollingScheduler;
import com.jumunhasyeo.hub.hub.application.HubService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
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
abstract class AbstractHubCacheComparisonMeasurementTest extends IntegrationTest {

    private static final int REQUESTS = Integer.getInteger("hubCache.measurement.requests", 3_000);
    private static final int CONCURRENCY = Integer.getInteger("hubCache.measurement.concurrency", 50);
    private static final int WARMUP_REQUESTS = Integer.getInteger("hubCache.measurement.warmupRequests", 500);
    private static final int ROUNDS = Integer.getInteger("hubCache.measurement.rounds", 5);

    @LocalServerPort
    private int port;

    @Autowired
    private HubService hubService;

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

    protected abstract String cacheMode();

    protected abstract Class<? extends HubService> expectedServiceType();

    @BeforeEach
    @Override
    protected void truncateTables() {
        super.truncateTables();
        clearCaches();
    }

    @Test
    @DisplayName("허브 단건 조회의 캐시 적용 전후 성능을 독립 실행 조건에서 측정한다")
    void measureHubFindByIdWithFixedCacheConfiguration() throws Exception {
        // given
        assertThat(hubService).isInstanceOf(expectedServiceType());
        assertThat(REQUESTS).isPositive();
        assertThat(CONCURRENCY).isPositive();
        assertThat(WARMUP_REQUESTS).isNotNegative();
        assertThat(ROUNDS).isPositive();

        UUID hubId = createHub();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        warmUp(client, hubId);
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        List<RoundResult> results = new ArrayList<>(ROUNDS);

        // when
        for (int round = 1; round <= ROUNDS; round++) {
            clearCaches();
            RequestResult primingRequest = sendGetHubRequest(client, hubId);
            assertThat(primingRequest.success()).isTrue();
            if ("REDIS".equals(cacheMode())) {
                assertThat(redisTemplate.hasKey(cacheKey(hubId))).isTrue();
            }

            statistics.clear();
            results.add(runRound(client, hubId, round, statistics));
        }

        // then
        results.forEach(result -> assertThat(result.successes()).isEqualTo(REQUESTS));
        if ("REDIS".equals(cacheMode())) {
            results.forEach(result -> assertThat(result.dbPreparedStatements()).isZero());
        } else {
            results.forEach(result -> assertThat(result.dbPreparedStatements()).isGreaterThanOrEqualTo(REQUESTS));
        }

        printResults(results);
    }

    private UUID createHub() {
        return transactionTemplate.execute(status -> {
            Hub hub = Hub.createBranchHub(
                    "캐시성능측정허브",
                    Address.of("서울시 송파구", Coordinate.of(37.5, 127.0))
            );
            hub.activate();
            entityManager.persist(hub);
            entityManager.flush();
            entityManager.clear();
            return hub.getHubId();
        });
    }

    private void warmUp(HttpClient client, UUID hubId) throws Exception {
        clearCaches();
        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            assertThat(sendGetHubRequest(client, hubId).success()).isTrue();
        }
    }

    private RoundResult runRound(
            HttpClient client,
            UUID hubId,
            int round,
            Statistics statistics
    ) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENCY);
        List<Callable<RequestResult>> tasks = new ArrayList<>(REQUESTS);
        for (int i = 0; i < REQUESTS; i++) {
            tasks.add(() -> sendGetHubRequest(client, hubId));
        }

        try {
            long started = System.nanoTime();
            List<Future<RequestResult>> futures = executorService.invokeAll(tasks);
            long elapsedNanos = System.nanoTime() - started;

            List<Long> latencies = new ArrayList<>(REQUESTS);
            for (Future<RequestResult> future : futures) {
                RequestResult requestResult = future.get(10, TimeUnit.SECONDS);
                if (requestResult.success()) {
                    latencies.add(requestResult.elapsedNanos());
                }
            }
            latencies.sort(Comparator.naturalOrder());

            return RoundResult.from(
                    cacheMode(),
                    round,
                    REQUESTS,
                    latencies,
                    elapsedNanos,
                    statistics.getPrepareStatementCount()
            );
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private RequestResult sendGetHubRequest(HttpClient client, UUID hubId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/api/v1/hubs/" + hubId))
                .GET()
                .timeout(Duration.ofSeconds(10))
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

    private void printResults(List<RoundResult> results) {
        RoundSummary summary = RoundSummary.from(cacheMode(), results);

        System.out.printf(
                Locale.ROOT,
                "HUB_CACHE_CONDITION mode=%s requests=%d concurrency=%d warmupRequests=%d rounds=%d "
                        + "http=true postgres=16 redis=7 hikariMaxPool=5%n",
                cacheMode(),
                REQUESTS,
                CONCURRENCY,
                WARMUP_REQUESTS,
                ROUNDS
        );
        System.out.println("HUB_CACHE_CSV mode,round,requests,successes,avg_ms,p50_ms,p95_ms,p99_ms,rps,db_prepared_statements");
        results.forEach(result -> System.out.println("HUB_CACHE_CSV " + result.toCsv()));
        System.out.println("HUB_CACHE_SUMMARY " + summary.toReportLine());
    }

    private String cacheKey(UUID hubId) {
        return "hub::" + hubId;
    }

    private record RequestResult(boolean success, long elapsedNanos) {
    }

    private record RoundResult(
            String mode,
            int round,
            int requests,
            int successes,
            double averageMs,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double requestsPerSecond,
            long dbPreparedStatements
    ) {
        private static RoundResult from(
                String mode,
                int round,
                int requests,
                List<Long> sortedLatencies,
                long elapsedNanos,
                long dbPreparedStatements
        ) {
            double averageMs = sortedLatencies.stream()
                    .mapToDouble(RoundResult::nanosToMillis)
                    .average()
                    .orElse(0);

            return new RoundResult(
                    mode,
                    round,
                    requests,
                    sortedLatencies.size(),
                    averageMs,
                    percentile(sortedLatencies, 0.50),
                    percentile(sortedLatencies, 0.95),
                    percentile(sortedLatencies, 0.99),
                    requests / (elapsedNanos / 1_000_000_000.0),
                    dbPreparedStatements
            );
        }

        private String toCsv() {
            return String.format(
                    Locale.ROOT,
                    "%s,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.2f,%d",
                    mode,
                    round,
                    requests,
                    successes,
                    averageMs,
                    p50Ms,
                    p95Ms,
                    p99Ms,
                    requestsPerSecond,
                    dbPreparedStatements
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
    }

    private record RoundSummary(
            String mode,
            double medianAverageMs,
            double medianP50Ms,
            double medianP95Ms,
            double medianP99Ms,
            double medianRequestsPerSecond,
            double medianDbPreparedStatements
    ) {
        private static RoundSummary from(String mode, List<RoundResult> results) {
            return new RoundSummary(
                    mode,
                    median(results.stream().map(RoundResult::averageMs).toList()),
                    median(results.stream().map(RoundResult::p50Ms).toList()),
                    median(results.stream().map(RoundResult::p95Ms).toList()),
                    median(results.stream().map(RoundResult::p99Ms).toList()),
                    median(results.stream().map(RoundResult::requestsPerSecond).toList()),
                    median(results.stream().map(result -> (double) result.dbPreparedStatements()).toList())
            );
        }

        private String toReportLine() {
            return String.format(
                    Locale.ROOT,
                    "mode=%s medianAvgMs=%.3f medianP50Ms=%.3f medianP95Ms=%.3f "
                            + "medianP99Ms=%.3f medianRps=%.2f medianDbPreparedStatements=%.0f",
                    mode,
                    medianAverageMs,
                    medianP50Ms,
                    medianP95Ms,
                    medianP99Ms,
                    medianRequestsPerSecond,
                    medianDbPreparedStatements
            );
        }

        private static double median(List<Double> values) {
            List<Double> sorted = values.stream().sorted().toList();
            int middle = sorted.size() / 2;
            if (sorted.size() % 2 == 1) {
                return sorted.get(middle);
            }
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        }
    }
}
