package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hubRoute.application.HubRouteEventPublisher;
import com.jumunhasyeo.hub.hubRoute.application.command.BuildRouteCommand;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRoute;
import com.jumunhasyeo.hub.hubRoute.domain.repository.HubRouteRepository;
import com.jumunhasyeo.hub.hubRoute.domain.service.HubRouteDomainService;
import com.jumunhasyeo.hub.hubRoute.domain.vo.RouteWeight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "HUB_ROUTE_MEASUREMENT_SERVICE", matches = "true")
class HubCreateAsyncSeparationServiceMeasurementTest {

    private static final int EXISTING_BRANCH_COUNT = Integer.getInteger(
            "hubRoute.measurement.existingBranchCount",
            100
    );
    private static final int ROUTE_PAIRS = EXISTING_BRANCH_COUNT + 1;
    private static final int ROUTE_ROWS = ROUTE_PAIRS * 2;
    private static final int API_LATENCY_MS = Integer.getInteger("hubRoute.measurement.apiLatencyMs", 20);
    private static final int RUNS = Integer.getInteger("hubRoute.measurement.runs", 3);

    @Test
    @DisplayName("서비스 계층에서 경로 생성 비동기화 전후를 측정한다")
    void measure_hub_create_async_separation_at_service_level() {
        List<Measurement> syncRequest = new ArrayList<>();
        List<Measurement> asyncRequest = new ArrayList<>();
        List<Measurement> skeletonConsumer = new ArrayList<>();

        for (int i = 0; i < RUNS; i++) {
            syncRequest.add(runSyncHubCreateRequest());
            asyncRequest.add(runAsyncHubCreateRequest());
            skeletonConsumer.add(runAsyncSkeletonConsumer());
        }

        Measurement syncMedian = median(syncRequest);
        Measurement asyncMedian = median(asyncRequest);
        Measurement skeletonMedian = median(skeletonConsumer);
        double requestReduction = reductionPercent(syncMedian.elapsedMs(), asyncMedian.elapsedMs());
        double splitWorkReduction = reductionPercent(
                syncMedian.elapsedMs(),
                asyncMedian.elapsedMs() + skeletonMedian.elapsedMs()
        );

        System.out.println("=== Hub creation async route separation service measurement ===");
        System.out.printf(
                Locale.ROOT,
                "condition existingBranches=%d routePairs=%d directedRouteRows=%d apiLatencyMs=%d runs=%d%n",
                EXISTING_BRANCH_COUNT,
                ROUTE_PAIRS,
                ROUTE_ROWS,
                API_LATENCY_MS,
                RUNS
        );
        System.out.println("Before(sync request service): " + syncMedian.toReportLine());
        System.out.println("After(async request service): " + asyncMedian.toReportLine());
        System.out.println("Async skeleton consumer service: " + skeletonMedian.toReportLine());
        System.out.printf(
                Locale.ROOT,
                "Resume sentence: 생성 요청 응답 시간은 기존 동기 경로 생성 대비 %.2f%% 감소 "
                        + "(%,.3fms -> %,.3fms, 기존 지점 %d개/경로쌍 %d개/방향 경로 %d개, "
                        + "외부 API %dms 고정 지연 기준)%n",
                requestReduction,
                syncMedian.elapsedMs(),
                asyncMedian.elapsedMs(),
                EXISTING_BRANCH_COUNT,
                ROUTE_PAIRS,
                ROUTE_ROWS,
                API_LATENCY_MS
        );
        System.out.printf(
                Locale.ROOT,
                "Background note: 요청 응답과 비동기 skeleton 생성을 단순 합산해도 %.2f%% 감소 (%,.3fms -> %,.3fms)%n",
                splitWorkReduction,
                syncMedian.elapsedMs(),
                asyncMedian.elapsedMs() + skeletonMedian.elapsedMs()
        );

        assertThat(syncMedian.routeRows()).isEqualTo(ROUTE_ROWS);
        assertThat(syncMedian.apiCalls()).isEqualTo(ROUTE_PAIRS);
        assertThat(asyncMedian.routeRows()).isZero();
        assertThat(skeletonMedian.routeRows()).isEqualTo(ROUTE_ROWS);
        assertThat(syncMedian.elapsedMs()).isGreaterThan(asyncMedian.elapsedMs());
    }

    private static Measurement runSyncHubCreateRequest() {
        Scenario scenario = scenario();
        HubRouteDomainService domainService = new HubRouteDomainService();
        AtomicInteger apiCalls = new AtomicInteger();

        long started = System.nanoTime();
        Set<HubRoute> routes = domainService.buildRoutesForNewBranchHub(
                scenario.newBranch(),
                scenario.centerHub(),
                (from, to) -> {
                    apiCalls.incrementAndGet();
                    sleepApiLatency();
                    return RouteWeight.of(BigDecimal.valueOf(10.0), 20);
                }
        );

        return new Measurement(
                "sync-request-service",
                elapsedMs(started),
                routes.size(),
                0,
                apiCalls.get()
        );
    }

    private static Measurement runAsyncHubCreateRequest() {
        Scenario scenario = scenario();
        HubRepository hubRepository = mock(HubRepository.class);
        HubRepositoryCustom hubRepositoryCustom = mock(HubRepositoryCustom.class);
        HubEventPublisher hubEventPublisher = mock(HubEventPublisher.class);
        RouteProviderAvailabilityService routeProviderAvailabilityService = mock(RouteProviderAvailabilityService.class);
        HubServiceImpl hubService = new HubServiceImpl(
                hubRepository,
                hubRepositoryCustom,
                hubEventPublisher,
                routeProviderAvailabilityService
        );

        given(hubRepository.findById(scenario.centerHub().getHubId())).willReturn(Optional.of(scenario.centerHub()));
        given(hubRepository.save(any(Hub.class))).willAnswer(invocation -> invocation.getArgument(0));

        long started = System.nanoTime();
        hubService.create(CreateHubCommand.createBranch(
                scenario.centerHub().getHubId(),
                "new-branch",
                "new-address",
                37.7,
                127.2,
                HubType.BRANCH
        ));

        return new Measurement(
                "async-request-service",
                elapsedMs(started),
                0,
                1,
                0
        );
    }

    private static Measurement runAsyncSkeletonConsumer() {
        Scenario scenario = scenario();
        HubRepository hubRepository = mock(HubRepository.class);
        HubRouteRepository hubRouteRepository = mock(HubRouteRepository.class);
        HubRouteEventPublisher hubRouteEventPublisher = mock(HubRouteEventPublisher.class);
        HubRouteService hubRouteService = new HubRouteService(
                hubRepository,
                hubRouteRepository,
                new HubRouteDomainService(),
                hubRouteEventPublisher
        );
        BuildRouteCommand command = new BuildRouteCommand(
                scenario.centerHub().getHubId(),
                scenario.newBranch().getHubId(),
                scenario.newBranch().getName(),
                scenario.newBranch().getAddress(),
                HubType.BRANCH
        );

        given(hubRepository.findByIdIncludingCreating(scenario.newBranch().getHubId()))
                .willReturn(Optional.of(scenario.newBranch()));
        given(hubRepository.findById(scenario.centerHub().getHubId()))
                .willReturn(Optional.of(scenario.centerHub()));
        given(hubRouteRepository.findByStartHubOrEndHub(scenario.newBranch(), scenario.newBranch()))
                .willReturn(List.of());

        long started = System.nanoTime();
        hubRouteService.buildRoutesForNewHub(command);

        ArgumentCaptor<Set<HubRoute>> routeCaptor = ArgumentCaptor.forClass(Set.class);
        verify(hubRouteRepository).insertIgnore(routeCaptor.capture());

        return new Measurement(
                "skeleton-consumer-service",
                elapsedMs(started),
                routeCaptor.getValue().size(),
                0,
                0
        );
    }

    private static Scenario scenario() {
        Hub centerHub = hub("center", HubType.CENTER, 37.5, 127.0);
        centerHub.activate();

        for (int i = 0; i < EXISTING_BRANCH_COUNT; i++) {
            Hub existingBranch = hub("branch-" + i, HubType.BRANCH, 37.0 + (i * 0.001), 127.0 + (i * 0.001));
            existingBranch.activate();
            existingBranch.addCenterHub(centerHub);
        }

        Hub newBranch = hub("new-branch", HubType.BRANCH, 37.7, 127.2);
        newBranch.addCenterHub(centerHub);

        return new Scenario(centerHub, newBranch);
    }

    private static Hub hub(String name, HubType type, double lat, double lng) {
        return Hub.builder()
                .hubId(UUID.randomUUID())
                .name(name)
                .hubType(type)
                .address(Address.of("address", Coordinate.of(lat, lng)))
                .centerHubRelations(new HashSet<>())
                .branchHubRelations(new HashSet<>())
                .build();
    }

    private static void sleepApiLatency() {
        try {
            Thread.sleep(API_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("API latency simulation interrupted", e);
        }
    }

    private static double elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000.0;
    }

    private static Measurement median(List<Measurement> measurements) {
        return measurements.stream()
                .sorted(Comparator.comparingDouble(Measurement::elapsedMs))
                .skip(measurements.size() / 2L)
                .findFirst()
                .orElseThrow();
    }

    private static double reductionPercent(double beforeMs, double afterMs) {
        return ((beforeMs - afterMs) * 100.0) / beforeMs;
    }

    private record Scenario(Hub centerHub, Hub newBranch) {
    }

    private record Measurement(String name, double elapsedMs, int routeRows, int outboxEvents, int apiCalls) {
        String toReportLine() {
            return String.format(
                    Locale.ROOT,
                    "%s elapsedMs=%,.3f routeRows=%d outboxEvents=%d apiCalls=%d",
                    name,
                    elapsedMs,
                    routeRows,
                    outboxEvents,
                    apiCalls
            );
        }
    }
}
