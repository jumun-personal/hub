package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.stock.infrastructure.dynamic.StockLockType;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockRes;

import java.util.List;

public interface StockVariationService {
    StockLockType type();
    //상품 재고 감소
    StockRes decrement(DecreaseStockCommand command);
    //상품 재고 감소
    List<StockRes> decrement(List<DecreaseStockCommand> commands);
    //상품 재고 증가
    StockRes increment(IncreaseStockCommand command);
    //상품 재고 증가
    List<StockRes> increment(List<IncreaseStockCommand> commands);
}
