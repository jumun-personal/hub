package com.jumunhasyeo.stock.application.command;

import java.util.UUID;

public record DecreaseStockCommand(
        UUID hubId,
        UUID productId,
        Integer amount
) {
}
