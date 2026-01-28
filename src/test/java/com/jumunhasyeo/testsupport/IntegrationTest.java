package com.jumunhasyeo.testsupport;

import com.jumunhasyeo.CleanUp;
import com.jumunhasyeo.CommonTestContainer;
import com.jumunhasyeo.InternalIntegrationTestConfig;
import com.jumunhasyeo.stock.infrastructure.inbox.InboxDispatcher;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxDispatcher;
import com.jumunhasyeo.hub.hubRoute.infrastructure.event.HubRouteEventHandler;
import com.jumunhasyeo.stock.infrastructure.event.OrderCompensateHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({CleanUp.class, InternalIntegrationTestConfig.class})
public abstract class IntegrationTest extends CommonTestContainer {

    @Autowired
    protected CleanUp cleanUp;

    @BeforeEach
    protected void truncateTables() {
        cleanUp.truncateAll();
        beforeEachAfterTruncate();
    }

    @AfterEach
    protected void baseAfterEach() {
        afterEachCleanup();
    }

    protected void beforeEachAfterTruncate() {
    }

    protected void afterEachCleanup() {
    }

    @MockitoBean
    protected InboxDispatcher inboxDispatcher;

    @MockitoBean
    protected OutboxDispatcher outboxDispatcher;

    @MockitoBean
    protected OrderCompensateHandler orderCompensateHandler;

    @MockitoBean
    protected HubRouteEventHandler hubRouteEventHandler;
}
