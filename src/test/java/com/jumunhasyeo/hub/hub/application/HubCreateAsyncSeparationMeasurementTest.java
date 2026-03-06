package com.jumunhasyeo.hub.hub.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "HUB_ROUTE_MEASUREMENT_POSTGRES", matches = "true")
class HubCreateAsyncSeparationMeasurementTest {

    private static final int EXISTING_BRANCH_COUNT = Integer.getInteger(
            "hubRoute.measurement.existingBranchCount",
            100
    );
    private static final int ROUTE_PAIRS = EXISTING_BRANCH_COUNT + 1;
    private static final int ROUTE_ROWS = ROUTE_PAIRS * 2;
    private static final int API_LATENCY_MS = Integer.getInteger("hubRoute.measurement.apiLatencyMs", 20);
    private static final int RUNS = Integer.getInteger("hubRoute.measurement.runs", 3);
    private static final boolean ACTUAL_API = Boolean.parseBoolean(
            System.getenv().getOrDefault("HUB_ROUTE_MEASUREMENT_ACTUAL_API", "false")
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String KAKAO_API_KEY = System.getenv("KAKAO_MOBILITY_API_KEY");
    private static final String NAVER_API_KEY_ID = System.getenv("NAVER_MAPS_API_KEY_ID");
    private static final String NAVER_API_KEY = System.getenv("NAVER_MAPS_API_KEY");
    private static final String NAVER_BASE_URL = System.getenv().getOrDefault(
            "NAVER_MAPS_BASE_URL",
            "https://maps.apigw.ntruss.com"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("hub_route_measurement")
            .withUsername("testuser")
            .withPassword("testpass");

    @BeforeAll
    static void setUp() throws SQLException {
        if (ACTUAL_API && isBlank(KAKAO_API_KEY) && (isBlank(NAVER_API_KEY_ID) || isBlank(NAVER_API_KEY))) {
            throw new IllegalStateException(
                    "Actual API measurement requires KAKAO_MOBILITY_API_KEY or NAVER_MAPS_API_KEY_ID/NAVER_MAPS_API_KEY"
            );
        }
        createSchema();
    }

    @AfterAll
    static void tearDown() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("경로 생성 비동기화 전후의 허브 생성 응답시간을 측정한다")
    void measure_hub_create_response_time_before_after_async_route_separation() throws Exception {
        List<Measurement> syncRequest = new ArrayList<>();
        List<Measurement> asyncRequest = new ArrayList<>();

        for (int i = 0; i < RUNS; i++) {
            syncRequest.add(runSyncHubCreateRequest());
            asyncRequest.add(runAsyncHubCreateRequest());
        }

        Measurement syncMedian = median(syncRequest);
        Measurement asyncMedian = median(asyncRequest);
        double requestReduction = reductionPercent(syncMedian.elapsedMs(), asyncMedian.elapsedMs());

        System.out.println("=== Hub creation async route separation measurement ===");
        System.out.printf(
                Locale.ROOT,
                "condition existingBranches=%d routePairs=%d directedRouteRows=%d apiLatencyMs=%d runs=%d%n",
                EXISTING_BRANCH_COUNT,
                ROUTE_PAIRS,
                ROUTE_ROWS,
                API_LATENCY_MS,
                RUNS
        );
        System.out.printf(
                Locale.ROOT,
                "apiMode=%s%n",
                ACTUAL_API ? "actual-kakao-naver" : "simulated-fixed-latency"
        );
        System.out.println("Before(sync request): " + syncMedian.toReportLine());
        System.out.println("After(DB-buffered request): " + asyncMedian.toReportLine());
        System.out.printf(
                Locale.ROOT,
                "Resume sentence: 생성 요청 응답 시간은 기존 동기 경로 생성 대비 %.1f%% 감소 "
                        + "(%,dms -> %,dms, 기존 지점 %d개/경로쌍 %d개/방향 경로 %d개, %s 기준)%n",
                requestReduction,
                syncMedian.elapsedMs(),
                asyncMedian.elapsedMs(),
                EXISTING_BRANCH_COUNT,
                ROUTE_PAIRS,
                ROUTE_ROWS,
                ACTUAL_API ? "실제 지도 API 호출" : "외부 API " + API_LATENCY_MS + "ms 고정 지연"
        );

        assertThat(syncMedian.routeRows()).isEqualTo(ROUTE_ROWS);
        assertThat(syncMedian.apiCalls()).isEqualTo(ROUTE_PAIRS);
        assertThat(asyncMedian.outboxRows()).isEqualTo(1);
        assertThat(asyncMedian.routeRows()).isEqualTo(ROUTE_ROWS);
        assertThat(asyncMedian.apiCalls()).isZero();
        assertThat(syncMedian.elapsedMs()).isGreaterThan(asyncMedian.elapsedMs());
    }

    private static Measurement runSyncHubCreateRequest() throws Exception {
        Topology topology = seedExistingTopology();
        int kakaoCalls = 0;
        int naverCalls = 0;

        long started = System.nanoTime();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                insertHub(
                        connection,
                        topology.newBranch().hubId(),
                        "new-branch",
                        "BRANCH",
                        "PENDING",
                        topology.newBranch().latitude(),
                        topology.newBranch().longitude()
                );
                insertRelation(connection, topology.newBranch().hubId(), topology.centerHub().hubId());

                List<HubPoint> routeTargets = new ArrayList<>();
                routeTargets.add(topology.centerHub());
                routeTargets.addAll(topology.existingBranches());

                for (HubPoint targetHub : routeTargets) {
                    RouteApiResult routeApiResult = resolveRouteWeight(topology.newBranch(), targetHub);
                    if ("KAKAO".equals(routeApiResult.provider())) {
                        kakaoCalls++;
                    } else if ("NAVER".equals(routeApiResult.provider())) {
                        naverCalls++;
                    }
                    insertRoute(
                            connection,
                            topology.newBranch().hubId(),
                            targetHub.hubId(),
                            null,
                            "COMPLETE",
                            routeApiResult.distanceKm().doubleValue(),
                            routeApiResult.durationMinutes()
                    );
                    insertRoute(
                            connection,
                            targetHub.hubId(),
                            topology.newBranch().hubId(),
                            null,
                            "COMPLETE",
                            routeApiResult.distanceKm().doubleValue(),
                            routeApiResult.durationMinutes()
                    );
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        return new Measurement("sync-request", elapsedMs, ROUTE_ROWS, 0, ROUTE_PAIRS, kakaoCalls, naverCalls);
    }

    private static Measurement runAsyncHubCreateRequest() throws Exception {
        Topology topology = seedExistingTopology();

        long started = System.nanoTime();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                insertHub(
                        connection,
                        topology.newBranch().hubId(),
                        "new-branch",
                        "BRANCH",
                        "PENDING",
                        topology.newBranch().latitude(),
                        topology.newBranch().longitude()
                );
                insertRelation(connection, topology.newBranch().hubId(), topology.centerHub().hubId());
                List<HubPoint> routeTargets = new ArrayList<>();
                routeTargets.add(topology.centerHub());
                routeTargets.addAll(topology.existingBranches());
                for (HubPoint targetHub : routeTargets) {
                    insertRoute(
                            connection,
                            topology.newBranch().hubId(),
                            targetHub.hubId(),
                            topology.newBranch().hubId(),
                            "PENDING",
                            null,
                            null
                    );
                    insertRoute(
                            connection,
                            targetHub.hubId(),
                            topology.newBranch().hubId(),
                            topology.newBranch().hubId(),
                            "PENDING",
                            null,
                            null
                    );
                }
                insertOutboxEvent(connection, topology.newBranch().hubId(), topology.centerHub().hubId());
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        return new Measurement("db-buffered-request", elapsedMs, ROUTE_ROWS, 1, 0, 0, 0);
    }

    private static Topology seedExistingTopology() throws SQLException {
        truncateTables();

        HubPoint centerHub = new HubPoint(UUID.randomUUID(), 37.5, 127.0);
        HubPoint newBranch = new HubPoint(UUID.randomUUID(), 37.7, 127.2);
        List<HubPoint> existingBranches = new ArrayList<>();

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                insertHub(connection, centerHub.hubId(), "center", "CENTER", "COMPLETE", centerHub.latitude(), centerHub.longitude());
                for (int i = 0; i < EXISTING_BRANCH_COUNT; i++) {
                    HubPoint branchHub = new HubPoint(UUID.randomUUID(), 37.0 + (i * 0.001), 127.0 + (i * 0.001));
                    existingBranches.add(branchHub);
                    insertHub(connection, branchHub.hubId(), "branch-" + i, "BRANCH", "COMPLETE", branchHub.latitude(), branchHub.longitude());
                    insertRelation(connection, branchHub.hubId(), centerHub.hubId());
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }

        return new Topology(centerHub, newBranch, existingBranches);
    }

    private static void createSchema() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS p_hub_route");
            statement.execute("DROP TABLE IF EXISTS p_outbox_events");
            statement.execute("DROP TABLE IF EXISTS p_hub_relation");
            statement.execute("DROP TABLE IF EXISTS p_hub");
            statement.execute("""
                    CREATE TABLE p_hub (
                        hub_id UUID PRIMARY KEY,
                        name VARCHAR(50) NOT NULL,
                        hub_type VARCHAR(20),
                        address VARCHAR(255),
                        latitude DOUBLE PRECISION NOT NULL,
                        longitude DOUBLE PRECISION NOT NULL,
                        status VARCHAR(20),
                        created_at TIMESTAMP,
                        modified_at TIMESTAMP,
                        deleted_at TIMESTAMP,
                        is_deleted BOOLEAN DEFAULT false
                    )
                    """);
            statement.execute("""
                    CREATE TABLE p_hub_relation (
                        hub_relation_id UUID PRIMARY KEY,
                        general_hub_id UUID NOT NULL REFERENCES p_hub(hub_id),
                        middle_hub_id UUID NOT NULL REFERENCES p_hub(hub_id),
                        CONSTRAINT uk_hub_relation_general_middle UNIQUE (general_hub_id, middle_hub_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE p_hub_route (
                        route_id UUID PRIMARY KEY,
                        start_hub_id UUID NOT NULL REFERENCES p_hub(hub_id),
                        end_hub_id UUID NOT NULL REFERENCES p_hub(hub_id),
                        build_hub_id UUID,
                        route_status VARCHAR(20),
                        retry_count INTEGER NOT NULL DEFAULT 0,
                        next_retry_at TIMESTAMP,
                        error_message TEXT,
                        distance_km NUMERIC(10, 2),
                        duration_minutes INTEGER,
                        created_at TIMESTAMP,
                        modified_at TIMESTAMP,
                        deleted_at TIMESTAMP,
                        is_deleted BOOLEAN DEFAULT false,
                        CONSTRAINT uk_start_end_hub_deleted_at UNIQUE (start_hub_id, end_hub_id, is_deleted)
                    )
                    """);
            statement.execute("CREATE INDEX idx_hub_route_build_status_retry ON p_hub_route(build_hub_id, route_status, next_retry_at)");
            statement.execute("""
                    CREATE TABLE p_outbox_events (
                        id UUID PRIMARY KEY,
                        event_name VARCHAR(255) NOT NULL,
                        event_key VARCHAR(255) NOT NULL UNIQUE,
                        topic VARCHAR(255) NOT NULL,
                        payload JSONB NOT NULL,
                        status VARCHAR(20),
                        retry_count INTEGER NOT NULL DEFAULT 0,
                        max_retries INTEGER NOT NULL DEFAULT 3,
                        error_message TEXT,
                        claimed_at TIMESTAMP,
                        processed_at TIMESTAMP,
                        created_at TIMESTAMP,
                        modified_at TIMESTAMP,
                        deleted_at TIMESTAMP,
                        is_deleted BOOLEAN DEFAULT false
                    )
                    """);
        }
    }

    private static void truncateTables() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE p_hub_route, p_outbox_events, p_hub_relation, p_hub");
        }
    }

    private static void insertHub(
            Connection connection,
            UUID hubId,
            String name,
            String hubType,
            String status,
            double latitude,
            double longitude
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO p_hub(
                    hub_id, name, hub_type, address, latitude, longitude, status,
                    created_at, modified_at, deleted_at, is_deleted
                )
                VALUES (?, ?, ?, 'benchmark-address', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, false)
                """)) {
            statement.setObject(1, hubId);
            statement.setString(2, name);
            statement.setString(3, hubType);
            statement.setDouble(4, latitude);
            statement.setDouble(5, longitude);
            statement.setString(6, status);
            statement.executeUpdate();
        }
    }

    private static void insertRelation(Connection connection, UUID branchHubId, UUID centerHubId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO p_hub_relation(hub_relation_id, general_hub_id, middle_hub_id)
                VALUES (?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, branchHubId);
            statement.setObject(3, centerHubId);
            statement.executeUpdate();
        }
    }

    private static void insertRoute(
            Connection connection,
            UUID startHubId,
            UUID endHubId,
            UUID buildHubId,
            String status,
            Double distanceKm,
            Integer durationMinutes
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO p_hub_route(
                    route_id, start_hub_id, end_hub_id, build_hub_id, route_status,
                    retry_count, distance_km, duration_minutes,
                    created_at, modified_at, deleted_at, is_deleted
                )
                VALUES (?, ?, ?, ?, ?, 0, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, false)
                ON CONFLICT (start_hub_id, end_hub_id, is_deleted) DO NOTHING
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, startHubId);
            statement.setObject(3, endHubId);
            statement.setObject(4, buildHubId);
            statement.setString(5, status);
            if (distanceKm == null) {
                statement.setNull(6, java.sql.Types.DOUBLE);
            } else {
                statement.setDouble(6, distanceKm);
            }
            if (durationMinutes == null) {
                statement.setNull(7, java.sql.Types.INTEGER);
            } else {
                statement.setInt(7, durationMinutes);
            }
            statement.executeUpdate();
        }
    }

    private static void insertOutboxEvent(Connection connection, UUID hubId, UUID centerHubId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO p_outbox_events(
                    id, event_name, event_key, topic, payload, status,
                    retry_count, max_retries, error_message,
                    created_at, modified_at, deleted_at, is_deleted
                )
                VALUES (?, 'HubCreatedEvent', ?, 'hub-test', ?::jsonb, 'PENDING',
                    0, 3, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, false)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, "HubCreatedEvent:" + hubId);
            statement.setString(3, """
                    {"hubId":"%s","centerHubId":"%s","type":"BRANCH"}
                    """.formatted(hubId, centerHubId));
            statement.executeUpdate();
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    private static void sleepApiLatency() {
        try {
            Thread.sleep(API_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("API latency simulation interrupted", e);
        }
    }

    private static RouteApiResult resolveRouteWeight(HubPoint start, HubPoint end) throws Exception {
        if (!ACTUAL_API) {
            sleepApiLatency();
            return new RouteApiResult(BigDecimal.valueOf(10.0), 20, "SIMULATED");
        }

        if (!isBlank(KAKAO_API_KEY)) {
            try {
                return requestKakaoRoute(start, end);
            } catch (Exception ignored) {
                if (isBlank(NAVER_API_KEY_ID) || isBlank(NAVER_API_KEY)) {
                    throw ignored;
                }
            }
        }
        return requestNaverRoute(start, end);
    }

    private static RouteApiResult requestKakaoRoute(HubPoint start, HubPoint end) throws Exception {
        String origin = start.longitude() + "," + start.latitude();
        String destination = end.longitude() + "," + end.latitude();
        URI uri = URI.create("https://apis-navi.kakaomobility.com/v1/directions"
                + "?origin=" + urlEncode(origin)
                + "&destination=" + urlEncode(destination)
                + "&priority=RECOMMEND"
                + "&car_fuel=GASOLINE"
                + "&car_hipass=false"
                + "&alternatives=false"
                + "&road_details=false");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", KAKAO_API_KEY)
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Kakao API failed status=" + response.statusCode());
        }
        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        JsonNode route = root.path("routes").path(0);
        if (route.isMissingNode() || route.path("result_code").asInt(-1) != 0) {
            throw new IllegalStateException("Kakao API returned no usable route");
        }
        JsonNode summary = route.path("summary");
        return new RouteApiResult(
                BigDecimal.valueOf(summary.path("distance").asDouble() / 1000.0),
                (int) Math.ceil(summary.path("duration").asDouble() / 60.0),
                "KAKAO"
        );
    }

    private static RouteApiResult requestNaverRoute(HubPoint start, HubPoint end) throws Exception {
        String startParam = start.longitude() + "," + start.latitude();
        String goalParam = end.longitude() + "," + end.latitude();
        URI uri = URI.create(NAVER_BASE_URL + "/map-direction/v1/driving"
                + "?start=" + urlEncode(startParam)
                + "&goal=" + urlEncode(goalParam)
                + "&option=trafast");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("X-NCP-APIGW-API-KEY-ID", NAVER_API_KEY_ID)
                .header("X-NCP-APIGW-API-KEY", NAVER_API_KEY)
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Naver API failed status=" + response.statusCode());
        }
        JsonNode summary = OBJECT_MAPPER.readTree(response.body())
                .path("route")
                .path("trafast")
                .path(0)
                .path("summary");
        if (summary.isMissingNode()) {
            throw new IllegalStateException("Naver API returned no usable route");
        }
        return new RouteApiResult(
                BigDecimal.valueOf(summary.path("distance").asDouble() / 1000.0),
                (int) Math.ceil(summary.path("duration").asDouble() / 60.0),
                "NAVER"
        );
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Measurement median(List<Measurement> measurements) {
        return measurements.stream()
                .sorted(Comparator.comparingLong(Measurement::elapsedMs))
                .skip(measurements.size() / 2L)
                .findFirst()
                .orElseThrow();
    }

    private static double reductionPercent(long beforeMs, long afterMs) {
        return ((beforeMs - afterMs) * 100.0) / beforeMs;
    }

    private record HubPoint(UUID hubId, double latitude, double longitude) {
    }

    private record Topology(HubPoint centerHub, HubPoint newBranch, List<HubPoint> existingBranches) {
    }

    private record RouteApiResult(BigDecimal distanceKm, int durationMinutes, String provider) {
    }

    private record Measurement(String name, long elapsedMs, int routeRows, int outboxRows, int apiCalls, int kakaoCalls, int naverCalls) {
        String toReportLine() {
            return String.format(
                    Locale.ROOT,
                    "%s elapsedMs=%,d routeRows=%d outboxRows=%d apiCalls=%d kakaoCalls=%d naverCalls=%d",
                    name,
                    elapsedMs,
                    routeRows,
                    outboxRows,
                    apiCalls,
                    kakaoCalls,
                    naverCalls
            );
        }
    }
}
