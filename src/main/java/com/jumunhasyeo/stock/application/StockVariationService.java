package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.stock.infrastructure.dynamic.StockLockType;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;

import java.util.List;

public interface StockVariationService {
    StockLockType type();
    //상품 재고 감소
    List<StockChangeRes> decrement(String idempotencyKey, List<DecreaseStockCommand> commands);
    //상품 재고 증가
    List<StockChangeRes> increment(String idempotencyKey, List<IncreaseStockCommand> commands);
}
