package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.stock.infrastructure.dynamic.StockLockType;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Qualifier("stockVariationStrategy")
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "false", matchIfMissing = true)
public class StockVariationServicePessimisticLock implements StockVariationService {

    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;

    @Override
    public StockLockType type() {
        return StockLockType.PESSIMISTIC_LOCK;
    }

    @Override
    @Transactional
    public List<StockChangeRes> decrement(String idempotencyKey, List<DecreaseStockCommand> commands) {
        List<StockChangeRes> results = new ArrayList<>();

        for (DecreaseStockCommand command : commands) {
            Stock stock = getStockByPessimisticLock(command.hubId(), command.productId());
            stock.decrease(command.amount());
            results.add(StockChangeRes.decrease(
                    stock.getHubId(),
                    stock.getProductId(),
                    command.amount()
            ));
        }

        // 재고 차감 이력 저장
        saveDecreaseHistories(idempotencyKey, results);
        return results;
    }

    @Override
    @Transactional
    public List<StockChangeRes> increment(String idempotencyKey, List<IncreaseStockCommand> commands) {
        List<StockChangeRes> results = commands.stream()
                .map(this::increment)
                .toList();
        // 재고 증가 이력 저장
        saveIncreaseHistories(idempotencyKey, results);
        return results;
    }

    private StockChangeRes increment(IncreaseStockCommand command) {
        Stock stock = getStockByPessimisticLock(command.hubId(), command.productId());
        stock.increase(command.amount());
        return StockChangeRes.increase(stock.getHubId(), stock.getProductId(), command.amount());
    }

    private void saveDecreaseHistories(String idempotencyKey, List<StockChangeRes> results) {
        List<StockHistory> histories = results.stream()
                .map(result -> StockHistory.ofDecrease(
                        result.hubId(),
                        result.productId(),
                        result.quantity(),
                        idempotencyKey
                ))
                .toList();
        saveHistories(histories);
    }

    private void saveIncreaseHistories(String idempotencyKey, List<StockChangeRes> results) {
        List<StockHistory> histories = results.stream()
                .map(result -> StockHistory.ofIncrease(
                        result.hubId(),
                        result.productId(),
                        result.quantity(),
                        idempotencyKey
                ))
                .toList();
        saveHistories(histories);
    }

    private void saveHistories(List<StockHistory> histories) {
        try {
            stockHistoryRepository.saveAll(histories);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SUCCESS_CONFLICT_EXCEPTION, "이미 처리된 재고 변경 요청입니다.", e);
        }
    }

    private Stock getStockByPessimisticLock(UUID hubId, UUID productId) {
        return stockRepository.findByHubIdAndProductIdWithLock(hubId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "hubId = " + hubId + ", productId = " + productId));
    }
}
