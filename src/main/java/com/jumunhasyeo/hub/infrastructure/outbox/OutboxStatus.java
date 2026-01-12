package com.jumunhasyeo.hub.infrastructure.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OutboxStatus {
    PENDING("대기중"),
    COMPLETE("완료"),
    FAILED("실패");

    private final String description;
}
