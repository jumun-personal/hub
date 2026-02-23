package com.jumunhasyeo.stock.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import com.jumunhasyeo.stock.infrastructure.inbox.InboxService;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.jumunhasyeo.stock.domain.entity.StockHistory.StockHistoryType.DECREASE;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCompensateHandler {
    private final KafkaStockCompensationService kafkaStockCompensationService;
    private final StockHistoryRepository stockHistoryRepository;
    private final InboxService inboxService;

    @Transactional
    public void compensate(OrderCompensationEvent event) throws JsonProcessingException {
        List<StockHistory> histories = stockHistoryRepository.findByIdempotencyKeyAndType(event.getKey(), DECREASE);

        if (histories.isEmpty()) {
            log.warn("Stock decrease history not found. enqueue compensation to inbox. key={}", event.getKey());
            inboxService.save(event);
            return;
        }

        List<IncreaseStockCommand> payload = histories.stream()
                .map(history -> new IncreaseStockCommand(history.getHubId(), history.getProductId(), history.getQuantity()))
                .toList();
        kafkaStockCompensationService.incrementCompensation(cancelKey(event.getKey()), payload);
    }

    private String cancelKey(String idempotencyKey) {
        return "CANCEL_" + idempotencyKey;
    }
}
