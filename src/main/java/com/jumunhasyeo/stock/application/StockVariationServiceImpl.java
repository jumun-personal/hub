package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.stock.infrastructure.dynamic.StockLockType;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Primary
@Qualifier("stockVariationStrategy")
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "false", matchIfMissing = true)
public class StockVariationServiceImpl implements StockVariationService {

    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final EntityManager entityManager;

    @Override
    public StockLockType type() {
        return StockLockType.DEFAULT;
    }

    @Override
    public StockRes decrement(DecreaseStockCommand command) {
        Stock stock = getStock(command.productId());
        entityManager.detach(stock);
        stock.decrease(command.amount());
        StockRes res = StockRes.from(stock);
        stockRepository.decreaseStock(stock.getStockId(), command.amount());
        return res;
    }

    @Override
    @Transactional
    public List<StockRes> decrement(String idempotencyKey, List<DecreaseStockCommand> commands) {
        List<StockRes> results = commands.stream()
                .map(this::decrement)
                .toList();
        saveDecreaseHistories(idempotencyKey, results, commands);
        return results;
    }

    @Override
    public StockRes increment(IncreaseStockCommand command) {
        Stock stock = getStock(command.productId());
        entityManager.detach(stock);
        stock.increase(command.amount());
        StockRes res = StockRes.from(stock);
        stockRepository.increaseStock(stock.getStockId(), command.amount());
        return res;
    }

    @Override
    @Transactional
    public List<StockRes> increment(String idempotencyKey, List<IncreaseStockCommand> commands) {
        List<StockRes> results = commands.stream()
                .map(this::increment)
                .toList();
        saveIncreaseHistories(idempotencyKey, results, commands);
        return results;
    }

    private void saveDecreaseHistories(String idempotencyKey, List<StockRes> results, List<DecreaseStockCommand> commands) {
        List<StockHistory> histories = results.stream()
                .map(result -> StockHistory.ofDecrease(
                        result.hubId(),
                        result.productId(),
                        findDecreaseAmount(result.productId(), commands),
                        idempotencyKey
                ))
                .toList();
        saveHistories(histories);
    }

    private void saveIncreaseHistories(String idempotencyKey, List<StockRes> results, List<IncreaseStockCommand> commands) {
        List<StockHistory> histories = results.stream()
                .map(result -> StockHistory.ofIncrease(
                        result.hubId(),
                        result.productId(),
                        findIncreaseAmount(result.productId(), commands),
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

    private int findDecreaseAmount(UUID productId, List<DecreaseStockCommand> commands) {
        return commands.stream()
                .filter(command -> command.productId().equals(productId))
                .findFirst()
                .map(DecreaseStockCommand::amount)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "재고 감소 이력 수량을 찾을 수 없습니다."));
    }

    private int findIncreaseAmount(UUID productId, List<IncreaseStockCommand> commands) {
        return commands.stream()
                .filter(command -> command.productId().equals(productId))
                .findFirst()
                .map(IncreaseStockCommand::amount)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "재고 증가 이력 수량을 찾을 수 없습니다."));
    }

    private Stock getStock(UUID productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "productId = "+productId));
    }
}
