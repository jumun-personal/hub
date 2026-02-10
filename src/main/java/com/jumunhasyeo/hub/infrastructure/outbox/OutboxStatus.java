package com.jumunhasyeo.hub.infrastructure.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OutboxStatus {
    PENDING("대기중"),
    PROCESSING("처리중"),
    COMPLETE("완료"),
    FAILED("재시도 대기"),
    DEAD("최종 실패");

    private final String description;
}
