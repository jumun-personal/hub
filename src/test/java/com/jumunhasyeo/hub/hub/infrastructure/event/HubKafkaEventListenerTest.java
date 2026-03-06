package com.jumunhasyeo.hub.hub.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.hub.hub.application.HubCreationSagaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("HubRouteBuildCompletedEvent는 DB Job 전환 이후 saga를 호출하지 않고 skip 한다")
    void dispatch_buildCompleted_skipsLegacyEvent() throws Exception {
        String payload = "{}";
        String simpleClassName = "HubRouteBuildCompletedEvent";

        hubKafkaEventListener.dispatch(payload, simpleClassName);

        then(hubCreationSagaService).shouldHaveNoInteractions();
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
