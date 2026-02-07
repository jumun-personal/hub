package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.common.Idempotency.DbIdempotent;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.*;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.application.service.HubClient;
import com.jumunhasyeo.stock.application.service.ProductClient;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockVariationService stockVariationService;
    private final StockRepository stockRepository;
    private final HubClient hubClient;
    private final ProductClient productClient;

    //재고 추가
    @Transactional
    public StockRes create(CreateStockCommand command){
        if(!isExistHubAndProduct(command)){
            throw new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "허브 또는 상품이 존재하지 않습니다.");
        }
        Stock stock = Stock.of(command.hubId(), command.productId(), command.quantity());
        Stock save = stockRepository.save(stock);
        return StockRes.from(save);
    }

    //단건 조회
    public StockRes get(UUID stockId){
        Stock save = getStock(stockId);
        return StockRes.from(save);
    }

    //삭제
    @Transactional
    public StockRes delete(DeleteStockCommand command) {
        Stock stock = getStock(command.stockId());
        stock.markDeleted(command.userId());
        return StockRes.from(stock);
    }

    //상품 재고 감소
    @DbIdempotent(ttlDays = 1)
    public List<StockRes> decrement(String idempotencyKey, List<DecreaseStockCommand> commandList){
        List<DecreaseStockCommand> sortedList = new ArrayList<>(commandList);
        sortedList.sort(Comparator.comparing(DecreaseStockCommand::productId));

        return stockVariationService.decrement(idempotencyKey, sortedList);
    }

    //상품 재고 증가
    @DbIdempotent(ttlDays = 1)
    public List<StockRes> increment(String idempotencyKey, List<IncreaseStockCommand> commandList){
        List<IncreaseStockCommand> sortedList = new ArrayList<>(commandList);
        sortedList.sort(Comparator.comparing(IncreaseStockCommand::productId));

        return stockVariationService.increment(idempotencyKey, sortedList);
    }

    private Stock getStock(UUID stockId){
        return stockRepository.findById(stockId)
                .orElseThrow(()-> new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "stock(id="+ stockId +") 조회에 실패 했습니다."));
    }

    private boolean isExistHubAndProduct(CreateStockCommand command) {
        return hubClient.existHub(command.hubId()) && productClient.existProduct(command.productId());
    }
}
