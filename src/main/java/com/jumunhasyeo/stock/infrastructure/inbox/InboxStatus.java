package com.jumunhasyeo.stock.infrastructure.inbox;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum InboxStatus {
    RECEIVED("Kafka에서 수신, 처리 대기 중"),
    PROCESSING("현재 처리 중"),
    COMPLETED("처리 완료"),
    FAILED("처리 실패 (재시도 횟수 초과)");
    private final String description;
}
