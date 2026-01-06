package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.application.command.DeleteHubCommand;
import com.jumunhasyeo.hub.hub.application.command.UpdateHubCommand;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HubRedisCachedDecoratorServiceTest {

    @Mock
    private HubService hubService;

    @InjectMocks
    private HubRedisCachedDecoratorService service;

    @Test
    @DisplayName("create는 내부 hubService에 위임한다.")
    void create_delegates() {
        CreateHubCommand command = CreateHubCommand.createCenter("센터", "주소", 1.0, 2.0, HubType.CENTER);
        HubRes expected = new HubRes(UUID.randomUUID(), "센터", "주소", 1.0, 2.0);
        when(hubService.create(command)).thenReturn(expected);

        HubRes result = service.create(command);

        assertThat(result).isEqualTo(expected);
        verify(hubService).create(command);
    }

    @Test
    @DisplayName("update는 내부 hubService에 위임한다.")
    void update_delegates() {
        UUID hubId = UUID.randomUUID();
        UpdateHubCommand command = new UpdateHubCommand(hubId, "수정", "주소", 3.0, 4.0);
        HubRes expected = new HubRes(hubId, "수정", "주소", 3.0, 4.0);
        when(hubService.update(command)).thenReturn(expected);

        HubRes result = service.update(command);

        assertThat(result).isEqualTo(expected);
        verify(hubService).update(command);
    }

    @Test
    @DisplayName("delete는 내부 hubService에 위임한다.")
    void delete_delegates() {
        DeleteHubCommand command = new DeleteHubCommand(UUID.randomUUID(), 1L);
        when(hubService.delete(command)).thenReturn(command.hubId());

        UUID result = service.delete(command);

        assertThat(result).isEqualTo(command.hubId());
        verify(hubService).delete(command);
    }

    @Test
    @DisplayName("getById는 내부 hubService에 위임한다.")
    void getById_delegates() {
        UUID hubId = UUID.randomUUID();
        HubRes expected = new HubRes(hubId, "허브", "주소", 11.0, 22.0);
        when(hubService.getById(hubId)).thenReturn(expected);

        HubRes result = service.getById(hubId);

        assertThat(result).isEqualTo(expected);
        verify(hubService).getById(hubId);
    }

    @Test
    @DisplayName("search는 내부 hubService에 위임한다.")
    void search_delegates() {
        HubSearchCondition condition = HubSearchCondition.builder().name("허브").build();
        Page<HubRes> expected = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(hubService.search(condition, PageRequest.of(0, 10))).thenReturn(expected);

        Page<HubRes> result = service.search(condition, PageRequest.of(0, 10));

        assertThat(result).isEqualTo(expected);
        verify(hubService).search(condition, PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("existById는 내부 hubService에 위임한다.")
    void existById_delegates() {
        UUID hubId = UUID.randomUUID();
        when(hubService.existById(hubId)).thenReturn(Boolean.TRUE);

        Boolean result = service.existById(hubId);

        assertThat(result).isTrue();
        verify(hubService).existById(hubId);
    }

    @Test
    @DisplayName("getAll은 내부 hubService에 위임한다.")
    void getAll_delegates() {
        List<HubRes> expected = List.of(new HubRes(UUID.randomUUID(), "허브", "주소", 1.0, 1.0));
        when(hubService.getAll()).thenReturn(expected);

        List<HubRes> result = service.getAll();

        assertThat(result).isEqualTo(expected);
        verify(hubService).getAll();
    }
}
