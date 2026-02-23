package com.jumunhasyeo.stock.infrastructure.inbox;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import com.jumunhasyeo.stock.infrastructure.event.KafkaStockCompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jumunhasyeo.stock.domain.entity.StockHistory.StockHistoryType.DECREASE;
import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_CANCEL_EVENT;
import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_ROLLED_BACK_EVENT;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxDispatcher {

    private final StockHistoryRepository stockHistoryRepository;
    private final KafkaStockCompensationService kafkaStockCompensationService;

    public void dispatch(InboxEvent event) {
        log.info("Retrying event: {} (type: {}, attempt: {})",
                event.getEventKey(), event.getEventName(), event.getRetryCount() + 1);
        List<StockHistory> histories = stockHistoryRepository.findByIdempotencyKeyAndType(event.getEventKey(), DECREASE);

        try {
            if (histories.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "원본 재고 감소 이력을 찾을 수 없습니다. key=" + event.getEventKey());
            }

            // 이벤트 타입에 따라 처리
            if (event.getEventName().equals(ORDER_CANCEL_EVENT.getEventName())) {
                kafkaStockCompensationService.incrementCompensation(cancelKey(event.getEventKey()), toPayload(histories));
            } else if (event.getEventName().equals(ORDER_ROLLED_BACK_EVENT.getEventName())){
                kafkaStockCompensationService.incrementCompensation(cancelKey(event.getEventKey()), toPayload(histories));
            } else {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 Inbox 이벤트 타입입니다. type=" + event.getEventName());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Inbox 이벤트 처리 중 오류가 발생했습니다.", e);
        }
    }

    private List<IncreaseStockCommand> toPayload(List<StockHistory> histories) {
        return histories.stream()
                .map(history -> new IncreaseStockCommand(history.getHubId(), history.getProductId(), history.getQuantity()))
                .toList();
    }

    private String cancelKey(String idempotencyKey) {
        return "CANCEL_" + idempotencyKey;
    }
}
