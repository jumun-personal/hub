package com.jumunhasyeo.stock.application.command;

import java.util.UUID;

public record IncreaseStockCommand(
        UUID hubId,
        UUID productId,
        int amount
) {
}
