package com.jumunhasyeo.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.scheduler.HubRouteBuildScheduler;
import com.jumunhasyeo.common.scheduler.HubRouteRefreshScheduler;
import com.jumunhasyeo.common.scheduler.InboxPollingScheduler;
import com.jumunhasyeo.common.scheduler.OutboxPollingScheduler;
import com.jumunhasyeo.hub.hub.application.HubCreationSagaService;
import com.jumunhasyeo.hub.hub.infrastructure.event.HubKafkaEventListener;
import com.jumunhasyeo.hub.hubRoute.application.service.HubRouteService;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteWorkLifecycle;
import com.jumunhasyeo.hub.hubRoute.application.service.RouteProviderAvailabilityService;
import com.jumunhasyeo.hub.hubRoute.infrastructure.event.HubRouteEventHandler;
import com.jumunhasyeo.hub.hubRoute.infrastructure.event.HubRouteKafkaEventListener;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxPublicationService;
import com.jumunhasyeo.stock.infrastructure.event.KafkaStockEventListener;
import com.jumunhasyeo.stock.infrastructure.event.OrderAclService;
import com.jumunhasyeo.stock.infrastructure.event.OrderCompensateHandler;
import com.jumunhasyeo.stock.infrastructure.inbox.InboxService;
import com.jumunhasyeo.stock.infrastructure.inbox.JpaInboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeRoleContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class))
            .withBean(HubCreationSagaService.class, () -> mock(HubCreationSagaService.class))
            .withBean(HubRouteEventHandler.class, () -> mock(HubRouteEventHandler.class))
            .withBean(HubRouteService.class, () -> mock(HubRouteService.class))
            .withBean(RouteWorkLifecycle.class, () -> mock(RouteWorkLifecycle.class))
            .withBean(RouteProviderAvailabilityService.class, () -> mock(RouteProviderAvailabilityService.class))
            .withBean(OrderCompensateHandler.class, () -> mock(OrderCompensateHandler.class))
            .withBean(OrderAclService.class, () -> mock(OrderAclService.class))
            .withBean(InboxService.class, () -> mock(InboxService.class))
            .withBean(JpaInboxRepository.class, () -> mock(JpaInboxRepository.class))
            .withBean(OutboxPublicationService.class, () -> mock(OutboxPublicationService.class))
            .withUserConfiguration(
                    HubKafkaEventListener.class,
                    HubRouteKafkaEventListener.class,
                    HubRouteBuildScheduler.class,
                    HubRouteRefreshScheduler.class,
                    KafkaStockEventListener.class,
                    InboxPollingScheduler.class,
                    OutboxPollingScheduler.class
            );

    @Test
    @DisplayName("hub-api 역할은 HTTP 도메인 Listener만 로드하고 Route Worker 작업은 로드하지 않는다.")
    void hubApiRole_loadsApiBeansOnly() {
        contextRunner
                .withPropertyValues(
                        "hub.api.events.enabled=true",
                        "hub.route.worker.enabled=false",
                        "hub.route.build.scheduler.enabled=false",
                        "hub.route.refresh.enabled=false",
                        "stock.events.enabled=true",
                        "stock.inbox.scheduler.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(HubKafkaEventListener.class);
                    assertThat(context).doesNotHaveBean(HubRouteKafkaEventListener.class);
                    assertThat(context).doesNotHaveBean(HubRouteBuildScheduler.class);
                    assertThat(context).doesNotHaveBean(HubRouteRefreshScheduler.class);
                    assertThat(context).hasSingleBean(KafkaStockEventListener.class);
                    assertThat(context).hasSingleBean(InboxPollingScheduler.class);
                    assertThat(context).hasSingleBean(OutboxPollingScheduler.class);
                });
    }

    @Test
    @DisplayName("route-worker 역할은 Route 작업만 로드하고 Hub·Stock Listener는 로드하지 않는다.")
    void routeWorkerRole_loadsRouteBeansOnly() {
        contextRunner
                .withPropertyValues(
                        "hub.api.events.enabled=false",
                        "hub.route.worker.enabled=true",
                        "hub.route.build.scheduler.enabled=true",
                        "hub.route.refresh.enabled=true",
                        "stock.events.enabled=false",
                        "stock.inbox.scheduler.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HubKafkaEventListener.class);
                    assertThat(context).hasSingleBean(HubRouteKafkaEventListener.class);
                    assertThat(context).hasSingleBean(HubRouteBuildScheduler.class);
                    assertThat(context).hasSingleBean(HubRouteRefreshScheduler.class);
                    assertThat(context).doesNotHaveBean(KafkaStockEventListener.class);
                    assertThat(context).doesNotHaveBean(InboxPollingScheduler.class);
                    assertThat(context).hasSingleBean(OutboxPollingScheduler.class);
                });
    }
}
