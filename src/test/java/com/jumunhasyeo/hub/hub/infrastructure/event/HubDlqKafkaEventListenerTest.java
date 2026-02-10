package com.jumunhasyeo.hub.hub.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.hub.hub.application.HubCreationSagaService;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.event.HubCreatedEvent;
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
class HubDlqKafkaEventListenerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HubCreationSagaService hubCreationSagaService;

    @InjectMocks
    private HubDlqKafkaEventListener hubDlqKafkaEventListener;

    @Test
    @DisplayName("DLQ의 HubCreatedEvent를 처리하면 saga compensate가 호출된다")
    void dispatch_hubCreated_callsCompensate() throws Exception {
        String payload = "{}";
        String reason = "map api failed";
        UUID hubId = UUID.randomUUID();
        HubCreatedEvent event = HubCreatedEvent.centerHub(
                Hub.builder()
                        .hubId(hubId)
                        .name("테스트 허브")
                        .hubType(HubType.CENTER)
                        .build()
        );
        given(objectMapper.readValue(payload, HubCreatedEvent.class)).willReturn(event);

        hubDlqKafkaEventListener.dispatch(payload, "HubCreatedEvent", reason);

        then(hubCreationSagaService).should().compensate(hubId, reason);
    }

    @Test
    @DisplayName("DLQ 실패 사유가 없으면 기본 사유로 compensate가 호출된다")
    void dispatch_whenReasonMissing_usesDefaultReason() throws Exception {
        String payload = "{}";
        UUID hubId = UUID.randomUUID();
        HubCreatedEvent event = HubCreatedEvent.centerHub(
                Hub.builder()
                        .hubId(hubId)
                        .name("테스트 허브")
                        .hubType(HubType.CENTER)
                        .build()
        );
        given(objectMapper.readValue(payload, HubCreatedEvent.class)).willReturn(event);

        hubDlqKafkaEventListener.dispatch(payload, "HubCreatedEvent", null);

        then(hubCreationSagaService).should().compensate(
                hubId,
                "Hub route build failed and moved to DLQ"
        );
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 listen은 skip 한다")
    void listen_whenHeaderMissing_skip() throws Exception {
        hubDlqKafkaEventListener.listen("{}", null, "reason");

        then(hubCreationSagaService).should(never()).compensate(any(), any());
    }

    @Test
    @DisplayName("미지원 eventType이면 listen은 skip 한다")
    void listen_whenUnsupportedType_skip() throws Exception {
        hubDlqKafkaEventListener.listen("{}", "UNKNOWN", "reason");

        then(hubCreationSagaService).should(never()).compensate(any(), any());
    }
}
