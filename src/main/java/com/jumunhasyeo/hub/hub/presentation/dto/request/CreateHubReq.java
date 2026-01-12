package com.jumunhasyeo.hub.hub.presentation.dto.request;

import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "허브 생성 요청")
public record CreateHubReq(
        @Schema(description = "지점허브를 추가할 경우 center hubID를 입력해야 합니다.", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID centerHubId,

        @Schema(description = "허브 이름", example = "서울특별시 센터", required = true)
        @NotBlank(message = "허브 이름은 필수입니다")
        String name,

        @Schema(description = "허브 주소", example = "서울특별시 송파구 송파대로 55", required = true)
        @NotBlank(message = "허브 주소는 필수입니다")
        String address,

        @Schema(description = "위도", example = "37.4783091", required = true)
        @NotNull(message = "위도는 필수입니다")
        Double latitude,

        @Schema(description = "경도", example = "127.1230678", required = true)
        @NotNull(message = "경도는 필수입니다")
        Double longitude,

        @Schema(description = "허브 유형", example = "CETNER", required = true)
        HubType hubType
) {
        public boolean validate() { //branch hub 생성은 centerHubId 필수
                return !(HubType.BRANCH.equals(this.hubType()) && this.centerHubId() == null);
        }
}
