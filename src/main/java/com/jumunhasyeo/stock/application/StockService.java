package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.common.Idempotency.Idempotent;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.*;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
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
    private static final long STOCK_PROCESSING_TTL_SECONDS = 300L;
    private static final long STOCK_SUCCESS_TTL_SECONDS = 86_400L;

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
    @Idempotent(
            processingTtlSeconds = STOCK_PROCESSING_TTL_SECONDS,
            successTtlSeconds = STOCK_SUCCESS_TTL_SECONDS
    )
    public List<StockChangeRes> decrement(String idempotencyKey, List<DecreaseStockCommand> commandList){
        validateDecreaseCommands(commandList);
        validateUniqueDecreaseTargets(commandList);

        // 데드락 방지를 위해 항상 동일한 재고 식별자(hubId, productId) 순서로 처리한다.
        List<DecreaseStockCommand> sortedList = commandList.stream()
                .sorted(Comparator.comparing(DecreaseStockCommand::hubId)
                        .thenComparing(DecreaseStockCommand::productId))
                .toList();

        // 재고 변경 방식은 조건부 UPDATE, 비관적 락 등으로 교체될 수 있어 별도 서비스에 위임한다.
        return stockVariationService.decrement(idempotencyKey, sortedList);
    }

    //상품 재고 증가
    @Idempotent(
            processingTtlSeconds = STOCK_PROCESSING_TTL_SECONDS,
            successTtlSeconds = STOCK_SUCCESS_TTL_SECONDS
    )
    public List<StockChangeRes> increment(String idempotencyKey, List<IncreaseStockCommand> commandList){
        validateIncreaseCommands(commandList);
        validateUniqueIncreaseTargets(commandList);

        // 데드락 방지를 위해 항상 동일한 재고 식별자(hubId, productId) 순서로 처리한다.
        List<IncreaseStockCommand> sortedList = commandList.stream()
                .sorted(Comparator.comparing(IncreaseStockCommand::hubId)
                        .thenComparing(IncreaseStockCommand::productId))
                .toList();

        // 재고 변경 방식은 조건부 UPDATE, 비관적 락 등으로 교체될 수 있어 별도 서비스에 위임한다.
        return stockVariationService.increment(idempotencyKey, sortedList);
    }

    private void validateUniqueDecreaseTargets(List<DecreaseStockCommand> commands) {
        Set<StockItemKey> stockItemKeys = new HashSet<>();
        boolean hasDuplicate = commands.stream()
                .map(command -> new StockItemKey(command.hubId(), command.productId()))
                .anyMatch(stockItemKey -> !stockItemKeys.add(stockItemKey));

        if (hasDuplicate) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "중복된 허브 상품 재고 감소 요청입니다.");
        }
    }

    private void validateDecreaseCommands(List<DecreaseStockCommand> commands) {
        commands.forEach(command -> validatePositiveAmount(command.amount()));
    }

    private void validateIncreaseCommands(List<IncreaseStockCommand> commands) {
        commands.forEach(command -> validatePositiveAmount(command.amount()));
    }

    private void validatePositiveAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.STOCK_VALID, "재고 변경 수량은 0보다 커야 합니다.");
        }
    }

    private void validateUniqueIncreaseTargets(List<IncreaseStockCommand> commands) {
        Set<StockItemKey> stockItemKeys = new HashSet<>();
        boolean hasDuplicate = commands.stream()
                .map(command -> new StockItemKey(command.hubId(), command.productId()))
                .anyMatch(stockItemKey -> !stockItemKeys.add(stockItemKey));

        if (hasDuplicate) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "중복된 허브 상품 재고 증가 요청입니다.");
        }
    }

    private Stock getStock(UUID stockId){
        return stockRepository.findById(stockId)
                .orElseThrow(()-> new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "stock(id="+ stockId +") 조회에 실패 했습니다."));
    }

    private boolean isExistHubAndProduct(CreateStockCommand command) {
        return hubClient.existHub(command.hubId()) && productClient.existProduct(command.productId());
    }

    private record StockItemKey(UUID hubId, UUID productId) {
    }
}
