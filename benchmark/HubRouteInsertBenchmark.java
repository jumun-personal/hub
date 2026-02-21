import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 경로 저장 최적화의 두 효과를 분리한다.
 *
 * 1. INDIVIDUAL_TX: N INSERT + N COMMIT
 * 2. SINGLE_TX:     N INSERT + 1 COMMIT
 * 3. JDBC_BULK:     1 executeBatch + 1 COMMIT
 *
 * 실행 예:
 * javac -cp postgresql.jar benchmark/HubRouteInsertBenchmark.java
 * java -cp benchmark:postgresql.jar HubRouteInsertBenchmark
 */
public class HubRouteInsertBenchmark {
    private static final String JDBC_URL = System.getenv().getOrDefault(
            "BENCH_JDBC_URL",
            "jdbc:postgresql://localhost:55432/benchdb?reWriteBatchedInserts=true"
    );
    private static final String USER = System.getenv().getOrDefault("BENCH_DB_USER", "bench");
    private static final String PASSWORD = System.getenv().getOrDefault("BENCH_DB_PASSWORD", "benchpass");
    private static final int ROUTE_COUNT = Integer.parseInt(
            System.getenv().getOrDefault("BENCH_ROUTE_COUNT", "200")
    );
    private static final int WARMUP_ROUNDS = Integer.parseInt(
            System.getenv().getOrDefault("BENCH_WARMUP_ROUNDS", "10")
    );
    private static final int MEASURE_ROUNDS = Integer.parseInt(
            System.getenv().getOrDefault("BENCH_MEASURE_ROUNDS", "50")
    );
    private static final String SYNCHRONOUS_COMMIT = System.getenv().getOrDefault(
            "BENCH_SYNCHRONOUS_COMMIT",
            "on"
    );

    private static final String INSERT_ROUTE = """
            INSERT INTO p_hub_route(
                route_id, start_hub_id, end_hub_id, distance_km, duration_minutes,
                created_at, modified_at, deleted_at, is_deleted
            )
            VALUES (?, ?, ?, 10.0, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, false)
            ON CONFLICT (start_hub_id, end_hub_id, is_deleted) DO NOTHING
            """;

    public static void main(String[] args) throws Exception {
        waitUntilReady();
        createSchema();
        List<RoutePair> routes = seedHubs();

        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (Strategy strategy : Strategy.values()) {
                truncateRoutes();
                measure(strategy, routes);
            }
        }

        Map<Strategy, List<Measurement>> measurements = new EnumMap<>(Strategy.class);
        Arrays.stream(Strategy.values()).forEach(strategy -> measurements.put(strategy, new ArrayList<>()));

        // 실행 순서에 따른 캐시·체크포인트 편향을 줄이기 위해 매 라운드 순서를 섞는다.
        for (int round = 0; round < MEASURE_ROUNDS; round++) {
            List<Strategy> shuffled = new ArrayList<>(List.of(Strategy.values()));
            Collections.shuffle(shuffled);
            for (Strategy strategy : shuffled) {
                truncateRoutes();
                Measurement measurement = measure(strategy, routes);
                if (countRoutes() != ROUTE_COUNT) {
                    throw new IllegalStateException("Expected " + ROUTE_COUNT + " rows");
                }
                measurements.get(strategy).add(measurement);
            }
        }

        System.out.println("routes=" + ROUTE_COUNT
                + ", warmup_rounds=" + WARMUP_ROUNDS
                + ", measure_rounds=" + MEASURE_ROUNDS
                + ", synchronous_commit=" + SYNCHRONOUS_COMMIT
                + ", jdbc_url=" + JDBC_URL);
        System.out.println("strategy,sql_execute_count,commit_count,total_median_ms,total_p95_ms,"
                + "jdbc_execute_median_ms,server_insert_median_ms,jdbc_minus_server_median_ms,"
                + "app_commit_median_ms,wal_write_count_median,wal_write_median_ms,"
                + "wal_sync_count_median,wal_sync_median_ms,wal_io_share_of_app_commit_pct,"
                + "app_commit_share_pct");
        for (Strategy strategy : Strategy.values()) {
            Summary summary = Summary.of(measurements.get(strategy));
            System.out.printf(
                    "%s,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,"
                            + "%d,%.3f,%d,%.3f,%.1f,%.1f%n",
                    strategy,
                    strategy.sqlExecuteCount(),
                    strategy.commitCount(),
                    nanosToMillis(summary.totalMedianNanos()),
                    nanosToMillis(summary.totalP95Nanos()),
                    nanosToMillis(summary.executeMedianNanos()),
                    nanosToMillis(summary.serverInsertMedianNanos()),
                    nanosToMillis(summary.jdbcMinusServerMedianNanos()),
                    nanosToMillis(summary.commitMedianNanos()),
                    summary.walWriteCountMedian(),
                    nanosToMillis(summary.walWriteMedianNanos()),
                    summary.walSyncCountMedian(),
                    nanosToMillis(summary.walSyncMedianNanos()),
                    summary.walIoShareOfAppCommitPercent(),
                    summary.appCommitSharePercent()
            );
        }
    }

    private static Measurement measure(Strategy strategy, List<RoutePair> routes) throws SQLException {
        resetServerStats();
        WalStats walBefore = readWalStats();
        Measurement clientMeasurement;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_ROUTE)) {
            connection.setAutoCommit(false);
            setSynchronousCommit(connection);
            clientMeasurement = switch (strategy) {
                case INDIVIDUAL_TX -> individualTransactions(connection, statement, routes);
                case SINGLE_TX -> singleTransaction(connection, statement, routes);
                case JDBC_BULK -> jdbcBulk(connection, statement, routes);
            };
        }
        return clientMeasurement.withServerStats(readServerInsertNanos(), readWalStats().minus(walBefore));
    }

    private static Measurement individualTransactions(
            Connection connection,
            PreparedStatement statement,
            List<RoutePair> routes
    ) throws SQLException {
        long executeNanos = 0;
        long commitNanos = 0;
        long totalStarted = System.nanoTime();
        for (RoutePair route : routes) {
            bindRoute(statement, route);
            long executeStarted = System.nanoTime();
            statement.executeUpdate();
            executeNanos += System.nanoTime() - executeStarted;

            long commitStarted = System.nanoTime();
            connection.commit();
            commitNanos += System.nanoTime() - commitStarted;
        }
        return Measurement.client(System.nanoTime() - totalStarted, executeNanos, commitNanos);
    }

    private static Measurement singleTransaction(
            Connection connection,
            PreparedStatement statement,
            List<RoutePair> routes
    ) throws SQLException {
        long executeNanos = 0;
        long totalStarted = System.nanoTime();
        for (RoutePair route : routes) {
            bindRoute(statement, route);
            long executeStarted = System.nanoTime();
            statement.executeUpdate();
            executeNanos += System.nanoTime() - executeStarted;
        }
        long commitStarted = System.nanoTime();
        connection.commit();
        long commitNanos = System.nanoTime() - commitStarted;
        return Measurement.client(System.nanoTime() - totalStarted, executeNanos, commitNanos);
    }

    private static Measurement jdbcBulk(
            Connection connection,
            PreparedStatement statement,
            List<RoutePair> routes
    ) throws SQLException {
        long totalStarted = System.nanoTime();
        for (RoutePair route : routes) {
            bindRoute(statement, route);
            statement.addBatch();
        }
        long executeStarted = System.nanoTime();
        statement.executeBatch();
        long executeNanos = System.nanoTime() - executeStarted;

        long commitStarted = System.nanoTime();
        connection.commit();
        long commitNanos = System.nanoTime() - commitStarted;
        return Measurement.client(System.nanoTime() - totalStarted, executeNanos, commitNanos);
    }

    private static void bindRoute(PreparedStatement statement, RoutePair route) throws SQLException {
        statement.setObject(1, UUID.randomUUID());
        statement.setObject(2, route.startHubId());
        statement.setObject(3, route.endHubId());
    }

    private static List<RoutePair> seedHubs() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE p_hub_route, p_hub");
        }

        UUID startHubId = UUID.randomUUID();
        insertHub(startHubId, "start");

        List<RoutePair> routes = new ArrayList<>();
        for (int i = 0; i < ROUTE_COUNT; i++) {
            UUID endHubId = UUID.randomUUID();
            insertHub(endHubId, "end-" + i);
            routes.add(new RoutePair(startHubId, endHubId));
        }
        return routes;
    }

    private static void insertHub(UUID hubId, String name) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO p_hub(
                         hub_id, name, latitude, longitude, created_at, modified_at, is_deleted
                     )
                     VALUES (?, ?, 37.5, 127.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
                     """)) {
            statement.setObject(1, hubId);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private static void createSchema() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS p_hub_route");
            statement.execute("DROP TABLE IF EXISTS p_hub");
            statement.execute("""
                    CREATE TABLE p_hub (
                        hub_id UUID PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        latitude DOUBLE PRECISION NOT NULL,
                        longitude DOUBLE PRECISION NOT NULL,
                        created_at TIMESTAMP,
                        modified_at TIMESTAMP,
                        is_deleted BOOLEAN DEFAULT false
                    )
                    """);
            statement.execute("""
                    CREATE TABLE p_hub_route (
                        route_id UUID PRIMARY KEY,
                        start_hub_id UUID NOT NULL REFERENCES p_hub(hub_id),
                        end_hub_id UUID NOT NULL REFERENCES p_hub(hub_id),
                        distance_km NUMERIC(10, 2) NOT NULL,
                        duration_minutes INTEGER NOT NULL,
                        created_at TIMESTAMP,
                        modified_at TIMESTAMP,
                        deleted_at TIMESTAMP,
                        is_deleted BOOLEAN DEFAULT false,
                        CONSTRAINT uk_hub_route UNIQUE (start_hub_id, end_hub_id, is_deleted)
                    )
                    """);
        }
    }

    private static void truncateRoutes() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE p_hub_route");
        }
    }

    private static int countRoutes() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM p_hub_route")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static void resetServerStats() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_stat_statements_reset()");
        }
    }

    private static long readServerInsertNanos() throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COALESCE(SUM(total_exec_time), 0)
                     FROM pg_stat_statements
                     WHERE query ILIKE 'INSERT INTO p_hub_route%'
                     """);
             var resultSet = statement.executeQuery()) {
            resultSet.next();
            return millisToNanos(resultSet.getDouble(1));
        }
    }

    private static WalStats readWalStats() throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT wal_write, wal_write_time, wal_sync, wal_sync_time
                     FROM pg_stat_wal
                     """);
             var resultSet = statement.executeQuery()) {
            resultSet.next();
            return new WalStats(
                    resultSet.getLong("wal_write"),
                    millisToNanos(resultSet.getDouble("wal_write_time")),
                    resultSet.getLong("wal_sync"),
                    millisToNanos(resultSet.getDouble("wal_sync_time"))
            );
        }
    }

    private static void setSynchronousCommit(Connection connection) throws SQLException {
        if (!SYNCHRONOUS_COMMIT.equals("on") && !SYNCHRONOUS_COMMIT.equals("off")) {
            throw new IllegalArgumentException("BENCH_SYNCHRONOUS_COMMIT must be on or off");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET synchronous_commit = " + SYNCHRONOUS_COMMIT);
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
    }

    private static void waitUntilReady() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            try (Connection ignored = openConnection()) {
                return;
            } catch (SQLException e) {
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("PostgreSQL did not become ready within 60 seconds");
    }

    private static long percentile(List<Long> values, double percentile) {
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static long millisToNanos(double millis) {
        return Math.round(millis * 1_000_000);
    }

    private enum Strategy {
        INDIVIDUAL_TX(ROUTE_COUNT, ROUTE_COUNT),
        SINGLE_TX(ROUTE_COUNT, 1),
        JDBC_BULK(1, 1);

        private final int sqlExecuteCount;
        private final int commitCount;

        Strategy(int sqlExecuteCount, int commitCount) {
            this.sqlExecuteCount = sqlExecuteCount;
            this.commitCount = commitCount;
        }

        int sqlExecuteCount() {
            return sqlExecuteCount;
        }

        int commitCount() {
            return commitCount;
        }
    }

    private record RoutePair(UUID startHubId, UUID endHubId) {
    }

    private record Measurement(
            long totalNanos,
            long executeNanos,
            long commitNanos,
            long serverInsertNanos,
            long walWriteCount,
            long walWriteNanos,
            long walSyncCount,
            long walSyncNanos
    ) {
        static Measurement client(long totalNanos, long executeNanos, long commitNanos) {
            return new Measurement(totalNanos, executeNanos, commitNanos, 0, 0, 0, 0, 0);
        }

        Measurement withServerStats(long insertNanos, WalStats walStats) {
            return new Measurement(
                    totalNanos,
                    executeNanos,
                    commitNanos,
                    insertNanos,
                    walStats.writeCount(),
                    walStats.writeNanos(),
                    walStats.syncCount(),
                    walStats.syncNanos()
            );
        }

        long jdbcMinusServerNanos() {
            return Math.max(0, executeNanos - serverInsertNanos);
        }

    }

    private record WalStats(
            long writeCount,
            long writeNanos,
            long syncCount,
            long syncNanos
    ) {
        WalStats minus(WalStats before) {
            return new WalStats(
                    Math.max(0, writeCount - before.writeCount),
                    Math.max(0, writeNanos - before.writeNanos),
                    Math.max(0, syncCount - before.syncCount),
                    Math.max(0, syncNanos - before.syncNanos)
            );
        }
    }

    private record Summary(
            long totalMedianNanos,
            long totalP95Nanos,
            long executeMedianNanos,
            long serverInsertMedianNanos,
            long jdbcMinusServerMedianNanos,
            long commitMedianNanos,
            long walWriteCountMedian,
            long walWriteMedianNanos,
            long walSyncCountMedian,
            long walSyncMedianNanos
    ) {
        static Summary of(List<Measurement> measurements) {
            return new Summary(
                    percentile(measurements.stream().map(Measurement::totalNanos).toList(), 0.50),
                    percentile(measurements.stream().map(Measurement::totalNanos).toList(), 0.95),
                    percentile(measurements.stream().map(Measurement::executeNanos).toList(), 0.50),
                    percentile(measurements.stream().map(Measurement::serverInsertNanos).toList(), 0.50),
                    percentile(measurements.stream().map(Measurement::jdbcMinusServerNanos).toList(), 0.50),
                    percentile(measurements.stream().map(Measurement::commitNanos).toList(), 0.50),
                    percentile(measurements.stream().map(Measurement::walWriteCount).toList(), 0.50),
                    percentile(measurements.stream().map(Measurement::walWriteNanos).toList(), 0.50),
                    percentile(measurements.stream().map(Measurement::walSyncCount).toList(), 0.50),
                    percentile(measurements.stream().map(Measurement::walSyncNanos).toList(), 0.50)
            );
        }

        double appCommitSharePercent() {
            return totalMedianNanos == 0 ? 0 : commitMedianNanos * 100.0 / totalMedianNanos;
        }

        double walIoShareOfAppCommitPercent() {
            return commitMedianNanos == 0
                    ? 0
                    : (walWriteMedianNanos + walSyncMedianNanos) * 100.0 / commitMedianNanos;
        }
    }
}
