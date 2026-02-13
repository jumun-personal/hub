package com.jumunhasyeo.hub.hub.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.hub.hub.application.HubCreationSagaService;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hubRoute.domain.event.HubRouteBuildCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HubKafkaEventListenerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HubCreationSagaService hubCreationSagaService;

    @InjectMocks
    private HubKafkaEventListener hubKafkaEventListener;

    @Test
    @DisplayName("HubRouteBuildCompletedEvent를 처리하면 saga complete가 호출된다")
    void dispatch_buildCompleted_callsComplete() throws Exception {
        String payload = "{}";
        String simpleClassName = "HubRouteBuildCompletedEvent";
        HubRouteBuildCompletedEvent event =
                new HubRouteBuildCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), HubType.CENTER);
        given(objectMapper.readValue(payload, HubRouteBuildCompletedEvent.class)).willReturn(event);

        hubKafkaEventListener.dispatch(payload, simpleClassName);

        then(hubCreationSagaService).should().complete(event.getHubId());
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 listen은 skip 한다")
    void listen_whenHeaderMissing_skip() throws Exception {
        hubKafkaEventListener.listen("{}", null);

        then(hubCreationSagaService).should(never()).complete(any());
        then(hubCreationSagaService).should(never()).compensate(any(), any());
    }

    @Test
    @DisplayName("미지원 eventType이면 listen은 skip 한다")
    void listen_whenUnsupportedType_skip() throws Exception {
        hubKafkaEventListener.listen("{}", "UNKNOWN");

        then(hubCreationSagaService).should(never()).complete(any());
        then(hubCreationSagaService).should(never()).compensate(any(), any());
    }
}
