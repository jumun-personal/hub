package com.jumunhasyeo.testsupport;

import com.jumunhasyeo.stock.infrastructure.inbox.InboxDispatcher;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxDispatcher;
import com.jumunhasyeo.hub.hubRoute.infrastructure.event.HubRouteEventHandler;
import com.jumunhasyeo.stock.infrastructure.event.OrderCompensateHandler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public abstract class AbstractEventDispatchIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    protected InboxDispatcher inboxDispatcher;

    @MockitoBean
    protected OutboxDispatcher outboxDispatcher;

    @MockitoBean
    protected OrderCompensateHandler orderCompensateHandler;

    @MockitoBean
    protected HubRouteEventHandler hubRouteEventHandler;
}
