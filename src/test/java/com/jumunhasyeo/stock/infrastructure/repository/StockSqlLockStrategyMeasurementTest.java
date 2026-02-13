package com.jumunhasyeo.stock.infrastructure.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

@Testcontainers
class StockSqlLockStrategyMeasurementTest {

    private static final int RUNS = 3;
    private static final int INITIAL_QUANTITY = 50;
    private static final int REQUEST_COUNT = 100;
    private static final int THREAD_COUNT = 50;
    private static final int POOL_SIZE = 5;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("stock_measurement")
            .withUsername("testuser")
            .withPassword("testpass")
            .withCommand("postgres", "-c", "max_connections=200");

    private static HikariDataSource dataSource;

    @BeforeAll
    static void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(POOL_SIZE);
        config.setMinimumIdle(POOL_SIZE);
        config.setConnectionTimeout(2_000);
        config.setPoolName("stock-sql-measurement");
        dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists p_stock_sql_measurement (
                        stock_id uuid primary key,
                        product_id uuid not null unique,
                        quantity integer not null,
                        is_deleted boolean not null default false
                    )
                    """);
        }
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("SQL만으로 SELECT FOR UPDATE와 조건부 UPDATE의 DB 락 점유 지표를 비교한다.")
    void measure_sql_only_stock_decrease_strategies() throws Exception {
        List<Measurement> pessimistic = measureRepeated(SqlStrategy.SELECT_FOR_UPDATE);
        List<Measurement> conditional = measureRepeated(SqlStrategy.CONDITIONAL_UPDATE);

        Measurement pessimisticMedian = median(pessimistic);
        Measurement conditionalMedian = median(conditional);

        System.out.println("=== SQL-only stock lock strategy measurement ===");
        System.out.println("Before(SELECT_FOR_UPDATE): " + pessimisticMedian.toReportLine());
        System.out.println("After(CONDITIONAL_UPDATE): " + conditionalMedian.toReportLine());
        System.out.printf(
                Locale.ROOT,
                "Resume sentence: 조건부 UPDATE 방식 전환 후 SQL 단독 동시 재고 차감 테스트에서 DB 작업 p95를 %.2fms에서 %.2fms로 낮추고, PostgreSQL lock wait sample을 %d회에서 %d회로 줄여 데드락 가능성을 낮추면서 affected row 기반 재고 부족 응답과 음수 재고 방지를 검증%n",
                pessimisticMedian.p95DbWorkMs(),
                conditionalMedian.p95DbWorkMs(),
                pessimisticMedian.lockWaitSamples(),
                conditionalMedian.lockWaitSamples()
        );

        assertThat(pessimisticMedian.successCount()).isEqualTo(INITIAL_QUANTITY);
        assertThat(conditionalMedian.successCount()).isEqualTo(INITIAL_QUANTITY);
        assertThat(pessimisticMedian.failureCount()).isEqualTo(REQUEST_COUNT - INITIAL_QUANTITY);
        assertThat(conditionalMedian.failureCount()).isEqualTo(REQUEST_COUNT - INITIAL_QUANTITY);
        assertThat(pessimisticMedian.finalQuantity()).isZero();
        assertThat(conditionalMedian.finalQuantity()).isZero();
        assertThat(pessimisticMedian.errorCount()).isZero();
        assertThat(conditionalMedian.errorCount()).isZero();
    }

    private List<Measurement> measureRepeated(SqlStrategy strategy) throws Exception {
        List<Measurement> measurements = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            measurements.add(measure(strategy));
        }
        return measurements;
    }

    private Measurement measure(SqlStrategy strategy) throws Exception {
        UUID stockId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        resetStock(stockId, productId);

        HikariSampler hikariSampler = new HikariSampler(dataSource.getHikariPoolMXBean());
        PgActivitySampler pgActivitySampler = new PgActivitySampler(strategy.applicationName());
        ConcurrentLinkedQueue<Long> responseDurations = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> dbWorkDurations = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(Math.min(REQUEST_COUNT, THREAD_COUNT));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

        hikariSampler.start();
        pgActivitySampler.start();
        long startedAt = System.nanoTime();
        for (int i = 0; i < REQUEST_COUNT; i++) {
            executorService.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long responseStartedAt = System.nanoTime();
                    OperationResult result = strategy.decrease(dataSource, productId, stockId);
                    responseDurations.add(System.nanoTime() - responseStartedAt);
                    dbWorkDurations.add(result.dbWorkNanos());
                    if (result.success()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        long elapsedNanos = System.nanoTime() - startedAt;
        pgActivitySampler.stop();
        hikariSampler.stop();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        return Measurement.from(
                strategy,
                successCount.get(),
                failureCount.get(),
                finalQuantity(stockId),
                errorCount.get(),
                responseDurations,
                dbWorkDurations,
                elapsedNanos,
                hikariSampler.maxActive(),
                hikariSampler.maxPending(),
                pgActivitySampler.maxActiveTransactions(),
                pgActivitySampler.maxTransactionAgeMs(),
                pgActivitySampler.lockWaitSamples()
        );
    }

    private void resetStock(UUID stockId, UUID productId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement truncate = connection.createStatement();
             PreparedStatement insert = connection.prepareStatement("""
                     insert into p_stock_sql_measurement(stock_id, product_id, quantity, is_deleted)
                     values (?, ?, ?, false)
                     """)) {
            truncate.execute("truncate table p_stock_sql_measurement");
            insert.setObject(1, stockId);
            insert.setObject(2, productId);
            insert.setInt(3, INITIAL_QUANTITY);
            insert.executeUpdate();
        }
    }

    private int finalQuantity(UUID stockId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select quantity
                     from p_stock_sql_measurement
                     where stock_id = ?
                     """)) {
            statement.setObject(1, stockId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private Measurement median(List<Measurement> measurements) {
        return measurements.stream()
                .sorted(Comparator.comparingDouble(Measurement::p95DbWorkMs))
                .toList()
                .get(measurements.size() / 2);
    }

    private enum SqlStrategy {
        SELECT_FOR_UPDATE {
            @Override
            OperationResult decrease(HikariDataSource dataSource, UUID productId, UUID stockId) throws SQLException {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    markTransaction(connection);
                    long dbStartedAt = System.nanoTime();
                    try (PreparedStatement select = connection.prepareStatement("""
                                 select stock_id, quantity
                                 from p_stock_sql_measurement
                                 where product_id = ?
                                   and is_deleted = false
                                 for update
                             """);
                         PreparedStatement update = connection.prepareStatement("""
                                 update p_stock_sql_measurement
                                 set quantity = quantity - 1
                                 where stock_id = ?
                             """)) {
                        select.setObject(1, productId);
                        try (ResultSet resultSet = select.executeQuery()) {
                            if (!resultSet.next() || resultSet.getInt("quantity") <= 0) {
                                connection.commit();
                                return new OperationResult(false, System.nanoTime() - dbStartedAt);
                            }
                            update.setObject(1, stockId);
                            update.executeUpdate();
                            connection.commit();
                            return new OperationResult(true, System.nanoTime() - dbStartedAt);
                        }
                    } catch (SQLException e) {
                        connection.rollback();
                        throw e;
                    }
                }
            }
        },
        CONDITIONAL_UPDATE {
            @Override
            OperationResult decrease(HikariDataSource dataSource, UUID productId, UUID stockId) throws SQLException {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    markTransaction(connection);
                    long dbStartedAt = System.nanoTime();
                    try (PreparedStatement update = connection.prepareStatement("""
                                 update p_stock_sql_measurement
                                 set quantity = quantity - 1
                                 where stock_id = ?
                                   and quantity >= 1
                             """)) {
                        update.setObject(1, stockId);
                        boolean success = update.executeUpdate() == 1;
                        connection.commit();
                        return new OperationResult(success, System.nanoTime() - dbStartedAt);
                    } catch (SQLException e) {
                        connection.rollback();
                        throw e;
                    }
                }
            }
        };

        abstract OperationResult decrease(HikariDataSource dataSource, UUID productId, UUID stockId) throws SQLException;

        String applicationName() {
            return "stock-sql-" + name();
        }

        void markTransaction(Connection connection) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("select set_config('application_name', ?, true)")) {
                statement.setString(1, applicationName());
                statement.execute();
            }
        }
    }

    private record OperationResult(
            boolean success,
            long dbWorkNanos
    ) {
    }

    private record Measurement(
            SqlStrategy strategy,
            int successCount,
            int failureCount,
            int finalQuantity,
            int errorCount,
            double averageResponseMs,
            double p95ResponseMs,
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
                SqlStrategy strategy,
                int successCount,
                int failureCount,
                int finalQuantity,
                int errorCount,
                ConcurrentLinkedQueue<Long> responseNanos,
                ConcurrentLinkedQueue<Long> dbWorkNanos,
                long elapsedNanos,
                int maxActiveConnections,
                int maxPendingConnections,
                int maxActiveTransactions,
                double maxTransactionAgeMs,
                int lockWaitSamples
        ) {
            Latency response = Latency.from(responseNanos);
            Latency dbWork = Latency.from(dbWorkNanos);
            return new Measurement(
                    strategy,
                    successCount,
                    failureCount,
                    finalQuantity,
                    errorCount,
                    response.averageMs(),
                    response.p95Ms(),
                    dbWork.averageMs(),
                    dbWork.p95Ms(),
                    dbWork.totalMs(),
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
                    "strategy=%s, success=%d, failure=%d, finalQuantity=%d, error=%d, responseAvg=%.2fms, responseP95=%.2fms, dbWorkAvg=%.2fms, dbWorkP95=%.2fms, dbWorkTotal=%.2fms, elapsed=%.2fms, hikariActiveMax=%d, hikariPendingMax=%d, pgActiveXactMax=%d, pgXactAgeMax=%.2fms, pgLockWaitSamples=%d",
                    strategy,
                    successCount,
                    failureCount,
                    finalQuantity,
                    errorCount,
                    averageResponseMs,
                    p95ResponseMs,
                    averageDbWorkMs,
                    p95DbWorkMs,
                    totalDbWorkMs,
                    elapsedMs,
                    maxActiveConnections,
                    maxPendingConnections,
                    maxActiveTransactions,
                    maxTransactionAgeMs,
                    lockWaitSamples
            );
        }
    }

    private record Latency(
            double averageMs,
            double p95Ms,
            double totalMs
    ) {
        static Latency from(ConcurrentLinkedQueue<Long> nanos) {
            List<Long> sorted = nanos.stream().sorted().toList();
            double averageMs = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
            int p95Index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
            double p95Ms = sorted.isEmpty() ? 0 : sorted.get(p95Index) / 1_000_000.0;
            double totalMs = sorted.stream().mapToLong(Long::longValue).sum() / 1_000_000.0;
            return new Latency(averageMs, p95Ms, totalMs);
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
            }, "sql-hikari-measurement-sampler");
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

    private static final class PgActivitySampler {
        private final String applicationName;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicInteger maxActiveTransactions = new AtomicInteger();
        private final AtomicInteger lockWaitSamples = new AtomicInteger();
        private volatile double maxTransactionAgeMs;
        private Thread thread;

        private PgActivitySampler(String applicationName) {
            this.applicationName = applicationName;
        }

        void start() {
            running.set(true);
            thread = new Thread(this::sampleUntilStopped, "sql-pg-activity-measurement-sampler");
            thread.start();
        }

        private void sampleUntilStopped() {
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(),
                    POSTGRES.getPassword()
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
                            maxActiveTransactions.accumulateAndGet(resultSet.getInt(1), Math::max);
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
