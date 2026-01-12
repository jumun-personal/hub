package com.jumunhasyeo.stock.infrastructure.dynamic;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * StockVariationService 구현체 타입
 * DEFAULT(update low lock) | PESSIMISTIC_LOCK
 */
@Getter
@RequiredArgsConstructor
public enum StockLockType {
    DEFAULT("UPDATE LOW LOCK"),
    PESSIMISTIC_LOCK("PESSIMISTIC_LOCK");

    private final String description;
}
