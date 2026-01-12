package com.jumunhasyeo.stock.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.Idempotency.db.application.DbIdempotentService;
import com.jumunhasyeo.common.Idempotency.db.domain.DbIdempotentKey;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import com.jumunhasyeo.stock.infrastructure.inbox.InboxService;
import com.jumunhasyeo.stock.application.StockService;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus.PROCESSING;
import static com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus.SUCCESS;

@Service
@RequiredArgsConstructor
public class OrderCompensateHandler {
    private final StockService stockService;
    private final DbIdempotentService dbIdempotentService;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void compensate(OrderCompensationEvent event) throws JsonProcessingException {
        DbIdempotentKey dbIdempotentKey = dbIdempotentService.get(event.getKey());

        if(dbIdempotentKey != null) {
            IdempotentStatus status = dbIdempotentKey.getStatus();
            if(PROCESSING.equals(status)) {
                inboxService.save(event);
            } else if(SUCCESS.equals(status)) {
                List<IncreaseStockCommand> payload = getPayload(dbIdempotentKey);
                stockService.increment(dbIdempotentKey.genCancelKey(), payload);
            }
        }
    }

    private List<IncreaseStockCommand> getPayload(DbIdempotentKey dbIdempotentKey) throws JsonProcessingException {
        return objectMapper.readValue(dbIdempotentKey.getPayload(), new TypeReference<>() {});
    }
}
