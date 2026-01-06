package com.jumunhasyeo.hub.hub.application.command;

import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateBranchHubCommandTest {

    @Test
    @DisplayName("CreateBranchHubCommand 필드 접근")
    void fields_access() {
        UUID centerHubId = UUID.randomUUID();
        CreateBranchHubCommand command = new CreateBranchHubCommand(
                centerHubId,
                "강남 지점",
                "서울 강남구",
                37.5,
                127.0,
                HubType.BRANCH
        );

        assertThat(command.centerHubId()).isEqualTo(centerHubId);
        assertThat(command.name()).isEqualTo("강남 지점");
        assertThat(command.address()).isEqualTo("서울 강남구");
        assertThat(command.latitude()).isEqualTo(37.5);
        assertThat(command.longitude()).isEqualTo(127.0);
        assertThat(command.hubType()).isEqualTo(HubType.BRANCH);
    }
}
