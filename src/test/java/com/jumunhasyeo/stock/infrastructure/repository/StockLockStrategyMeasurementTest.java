package com.jumunhasyeo.stock.infrastructure.repository;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.testsupport.IntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StockLockStrategyMeasurementTest extends IntegrationTest {

    private static final int RUNS = 3;
    private static final int INITIAL_QUANTITY = 50;
    private static final int REQUEST_COUNT = 100;
    private static final int THREAD_COUNT = 50;

    @Autowired
    private JpaStockRepository jpaStockRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private HikariDataSource dataSource;

    @Test
    @DisplayName("재고 차감 전략별 동시성 지표를 측정한다.")
    void measure_stock_lock_strategies() throws InterruptedException {
        List<Measurement> pessimistic = measureRepeated(StockStrategy.PESSIMISTIC_LOCK);
        List<Measurement> conditional = measureRepeated(StockStrategy.CONDITIONAL_UPDATE);

        Measurement pessimisticMedian = median(pessimistic);
        Measurement conditionalMedian = median(conditional);

        System.out.println("=== Stock lock strategy measurement ===");
        System.out.println("Before(PESSIMISTIC_LOCK): " + pessimisticMedian.toReportLine());
        System.out.println("After(CONDITIONAL_UPDATE): " + conditionalMedian.toReportLine());
        System.out.printf(
                Locale.ROOT,
                "Resume sentence: 조건부 UPDATE 방식 전환 후 동시 재고 차감 테스트에서 DB 작업 p95를 %.2fms에서 %.2fms로 낮추고, 데드락 없이 affected row 기반 재고 부족 응답과 음수 재고 방지를 검증%n",
                pessimisticMedian.p95DbWorkMs(),
                conditionalMedian.p95DbWorkMs()
        );

        assertThat(pessimisticMedian.finalQuantity()).isZero();
        assertThat(conditionalMedian.finalQuantity()).isZero();
        assertThat(pessimisticMedian.successCount()).isEqualTo(INITIAL_QUANTITY);
        assertThat(conditionalMedian.successCount()).isEqualTo(INITIAL_QUANTITY);
        assertThat(pessimisticMedian.deadlockOrTimeoutCount()).isZero();
        assertThat(conditionalMedian.deadlockOrTimeoutCount()).isZero();
    }

    private List<Measurement> measureRepeated(StockStrategy strategy) throws InterruptedException {
        List<Measurement> measurements = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            measurements.add(measure(strategy));
        }
        return measurements;
    }

    private Measurement measure(StockStrategy strategy) throws InterruptedException {
        UUID productId = UUID.randomUUID();
        UUID stockId = transactionTemplate.execute(status -> stockSave(productId, INITIAL_QUANTITY));
        HikariSampler hikariSampler = new HikariSampler(dataSource.getHikariPoolMXBean());
        DbActivitySampler dbActivitySampler = new DbActivitySampler(dataSource, strategy.applicationName());

        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> dbWorkDurations = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger deadlockOrTimeoutCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(Math.min(REQUEST_COUNT, THREAD_COUNT));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

        hikariSampler.start();
        dbActivitySampler.start();
        long startedAt = System.nanoTime();
        for (int i = 0; i < REQUEST_COUNT; i++) {
            executorService.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long requestStartedAt = System.nanoTime();
                    OperationResult result = strategy.decrease(jpaStockRepository, transactionTemplate, entityManager, productId, stockId);
                    latencies.add(System.nanoTime() - requestStartedAt);
                    dbWorkDurations.add(result.dbWorkNanos());
                    if (result.success()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (CannotAcquireLockException e) {
                    deadlockOrTimeoutCount.incrementAndGet();
                } catch (BusinessException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    if (isDeadlockOrTimeout(e)) {
                        deadlockOrTimeoutCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        long elapsedNanos = System.nanoTime() - startedAt;
        hikariSampler.stop();
        dbActivitySampler.stop();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        int finalQuantity = jpaStockRepository.findById(stockId)
                .orElseThrow()
                .getQuantity();

        return Measurement.from(
                strategy,
                successCount.get(),
                failureCount.get(),
                finalQuantity,
                deadlockOrTimeoutCount.get(),
                latencies,
                dbWorkDurations,
                elapsedNanos,
                hikariSampler.maxActive(),
                hikariSampler.maxPending(),
                dbActivitySampler.maxActiveTransactions(),
                dbActivitySampler.maxTransactionAgeMs(),
                dbActivitySampler.lockWaitSamples()
        );
    }

    private boolean isDeadlockOrTimeout(Exception e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("deadlock") ||
                        message.contains("timeout") ||
                        message.contains("could not obtain lock")
        );
    }

    private Measurement median(List<Measurement> measurements) {
        return measurements.stream()
                .sorted(Comparator.comparingDouble(Measurement::p95DbWorkMs))
                .toList()
                .get(measurements.size() / 2);
    }

    private UUID stockSave(UUID productId, int initQuantity) {
        Hub hub = Hub.builder()
                .name("허브-" + productId.toString().substring(0, 8))
                .status(HubStatus.COMPLETE)
                .address(Address.of("송파대로", Coordinate.of(12.6, 15.4)))
                .build();
        entityManager.persist(hub);
        entityManager.flush();

        Stock stock = Stock.of(hub.getHubId(), productId, initQuantity);
        entityManager.persist(stock);
        entityManager.flush();
        return stock.getStockId();
    }

    private enum StockStrategy {
        PESSIMISTIC_LOCK {
            @Override
            OperationResult decrease(
                    JpaStockRepository repository,
                    TransactionTemplate transactionTemplate,
                    EntityManager entityManager,
                    UUID productId,
                    UUID stockId
            ) {
                return transactionTemplate.execute(status -> {
                    markTransaction(entityManager);
                    long dbStartedAt = System.nanoTime();
                    Stock stock = repository.findStockByProductIdWithLock(productId).orElseThrow();
                    try {
                        stock.decrease(1);
                        return new OperationResult(true, System.nanoTime() - dbStartedAt);
                    } catch (BusinessException e) {
                        return new OperationResult(false, System.nanoTime() - dbStartedAt);
                    }
                });
            }
        },
        CONDITIONAL_UPDATE {
            @Override
            OperationResult decrease(
                    JpaStockRepository repository,
                    TransactionTemplate transactionTemplate,
                    EntityManager entityManager,
                    UUID productId,
                    UUID stockId
            ) {
                return transactionTemplate.execute(status -> {
                    markTransaction(entityManager);
                    long dbStartedAt = System.nanoTime();
                    boolean success = repository.decreaseStock(stockId, 1) == 1;
                    return new OperationResult(success, System.nanoTime() - dbStartedAt);
                });
            }
        };

        abstract OperationResult decrease(
                JpaStockRepository repository,
                TransactionTemplate transactionTemplate,
                EntityManager entityManager,
                UUID productId,
                UUID stockId
        );

        String applicationName() {
            return "stock-measure-" + name();
        }

        void markTransaction(EntityManager entityManager) {
            entityManager.createNativeQuery("select set_config('application_name', '" + applicationName() + "', true)")
                    .getSingleResult();
        }
    }

    private record OperationResult(
            boolean success,
            long dbWorkNanos
    ) {
    }

    private record Measurement(
            StockStrategy strategy,
            int successCount,
            int failureCount,
            int finalQuantity,
            int deadlockOrTimeoutCount,
            double averageLatencyMs,
            double p95LatencyMs,
            double averageDbWorkMs,
            double p95DbWorkMs,
            double totalDbWorkMs,
            double elapsedMs,
            int maxActiveConnections,
            int maxPendingConnections,
            int maxActiveTransactions,
            double maxTransactionAgeMs,
            int lockWaitSamples
    ) {
        static Measurement from(
                StockStrategy strategy,
                int successCount,
                int failureCount,
                int finalQuantity,
                int deadlockOrTimeoutCount,
                ConcurrentLinkedQueue<Long> latencyNanos,
                ConcurrentLinkedQueue<Long> dbWorkNanos,
                long elapsedNanos,
                int maxActiveConnections,
                int maxPendingConnections,
                int maxActiveTransactions,
                double maxTransactionAgeMs,
                int lockWaitSamples
        ) {
            List<Long> sortedLatencies = latencyNanos.stream().sorted().toList();
            double averageLatencyMs = sortedLatencies.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0) / 1_000_000.0;
            int p95Index = Math.max(0, (int) Math.ceil(sortedLatencies.size() * 0.95) - 1);
            double p95LatencyMs = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get(p95Index) / 1_000_000.0;
            List<Long> sortedDbWork = dbWorkNanos.stream().sorted().toList();
            double averageDbWorkMs = sortedDbWork.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0) / 1_000_000.0;
            int dbP95Index = Math.max(0, (int) Math.ceil(sortedDbWork.size() * 0.95) - 1);
            double p95DbWorkMs = sortedDbWork.isEmpty() ? 0 : sortedDbWork.get(dbP95Index) / 1_000_000.0;
            double totalDbWorkMs = sortedDbWork.stream()
                    .mapToLong(Long::longValue)
                    .sum() / 1_000_000.0;

            return new Measurement(
                    strategy,
                    successCount,
                    failureCount,
                    finalQuantity,
                    deadlockOrTimeoutCount,
                    averageLatencyMs,
                    p95LatencyMs,
                    averageDbWorkMs,
                    p95DbWorkMs,
                    totalDbWorkMs,
                    Duration.ofNanos(elapsedNanos).toNanos() / 1_000_000.0,
                    maxActiveConnections,
                    maxPendingConnections,
                    maxActiveTransactions,
                    maxTransactionAgeMs,
                    lockWaitSamples
            );
        }

        String toReportLine() {
            return String.format(
                    Locale.ROOT,
                    "strategy=%s, success=%d, failure=%d, finalQuantity=%d, responseAvg=%.2fms, responseP95=%.2fms, dbWorkAvg=%.2fms, dbWorkP95=%.2fms, dbWorkTotal=%.2fms, elapsed=%.2fms, deadlockOrTimeout=%d, hikariActiveMax=%d, hikariPendingMax=%d, pgActiveXactMax=%d, pgXactAgeMax=%.2fms, pgLockWaitSamples=%d",
                    strategy,
                    successCount,
                    failureCount,
                    finalQuantity,
                    averageLatencyMs,
                    p95LatencyMs,
                    averageDbWorkMs,
                    p95DbWorkMs,
                    totalDbWorkMs,
                    elapsedMs,
                    deadlockOrTimeoutCount,
                    maxActiveConnections,
                    maxPendingConnections,
                    maxActiveTransactions,
                    maxTransactionAgeMs,
                    lockWaitSamples
            );
        }
    }

    private static final class HikariSampler {
        private final HikariPoolMXBean pool;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicInteger maxActive = new AtomicInteger();
        private final AtomicInteger maxPending = new AtomicInteger();
        private Thread thread;

        private HikariSampler(HikariPoolMXBean pool) {
            this.pool = pool;
        }

        void start() {
            running.set(true);
            thread = new Thread(() -> {
                while (running.get()) {
                    maxActive.accumulateAndGet(pool.getActiveConnections(), Math::max);
                    maxPending.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "hikari-measurement-sampler");
            thread.start();
        }

        void stop() throws InterruptedException {
            running.set(false);
            thread.join(TimeUnit.SECONDS.toMillis(5));
        }

        int maxActive() {
            return maxActive.get();
        }

        int maxPending() {
            return maxPending.get();
        }
    }

    private static final class DbActivitySampler {
        private final HikariDataSource dataSource;
        private final String applicationName;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicInteger maxActiveTransactions = new AtomicInteger();
        private final AtomicInteger lockWaitSamples = new AtomicInteger();
        private volatile double maxTransactionAgeMs;
        private Thread thread;

        private DbActivitySampler(HikariDataSource dataSource, String applicationName) {
            this.dataSource = dataSource;
            this.applicationName = applicationName;
        }

        void start() {
            running.set(true);
            thread = new Thread(this::sampleUntilStopped, "pg-activity-measurement-sampler");
            thread.start();
        }

        private void sampleUntilStopped() {
            try (Connection connection = DriverManager.getConnection(
                    dataSource.getJdbcUrl(),
                    dataSource.getUsername(),
                    dataSource.getPassword()
            );
                 PreparedStatement statement = connection.prepareStatement("""
                         select count(*)::int,
                                coalesce(max(extract(epoch from clock_timestamp() - xact_start) * 1000), 0)::double precision,
                                count(*) filter (where wait_event_type = 'Lock')::int
                         from pg_stat_activity
                         where datname = current_database()
                           and application_name = ?
                           and xact_start is not null
                         """)) {
                statement.setString(1, applicationName);
                while (running.get()) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            int activeTransactions = resultSet.getInt(1);
                            maxActiveTransactions.accumulateAndGet(activeTransactions, Math::max);
                            maxTransactionAgeMs = Math.max(maxTransactionAgeMs, resultSet.getDouble(2));
                            lockWaitSamples.addAndGet(resultSet.getInt(3));
                        }
                    }
                    Thread.sleep(1);
                }
            } catch (SQLException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        void stop() throws InterruptedException {
            running.set(false);
            thread.join(TimeUnit.SECONDS.toMillis(5));
        }

        int maxActiveTransactions() {
            return maxActiveTransactions.get();
        }

        double maxTransactionAgeMs() {
            return maxTransactionAgeMs;
        }

        int lockWaitSamples() {
            return lockWaitSamples.get();
        }
    }
}
