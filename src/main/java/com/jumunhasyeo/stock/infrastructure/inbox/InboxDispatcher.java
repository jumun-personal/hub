package com.jumunhasyeo.stock.infrastructure.inbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.Idempotency.db.application.DbIdempotentService;
import com.jumunhasyeo.common.Idempotency.db.domain.DbIdempotentKey;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.StockService;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus.PROCESSING;
import static com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus.SUCCESS;
import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_CANCEL_EVENT;
import static com.jumunhasyeo.stock.infrastructure.event.ListenEventRegistry.ORDER_ROLLED_BACK_EVENT;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxDispatcher {

    private final DbIdempotentService dbIdempotentService;
    private final StockService stockService;
    private final ObjectMapper objectMapper;

    public void dispatch(InboxEvent event) {
        log.info("Retrying event: {} (type: {}, attempt: {})",
                event.getEventKey(), event.getEventName(), event.getRetryCount() + 1);
        DbIdempotentKey dbIdempotentKey = dbIdempotentService.get(event.getEventKey());

        try {
            if (dbIdempotentKey == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_EXCEPTION, "원본 멱등키를 찾을 수 없습니다. key=" + event.getEventKey());
            }
            if (PROCESSING.equals(dbIdempotentKey.getStatus())) {
                // 원본 트랜잭션 진행 중인 경우 일시 오류로 간주해 재시도 대상
                throw new BusinessException(ErrorCode.PROCESSING_CONFLICT_EXCEPTION);
            }
            if (!SUCCESS.equals(dbIdempotentKey.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "원본 멱등 상태가 유효하지 않습니다. key=" + event.getEventKey());
            }

            // 이벤트 타입에 따라 처리
            if (event.getEventName().equals(ORDER_CANCEL_EVENT.getEventName())) {
                stockService.increment(dbIdempotentKey.genCancelKey(), getPayload(dbIdempotentKey));
            } else if (event.getEventName().equals(ORDER_ROLLED_BACK_EVENT.getEventName())){
                stockService.increment(dbIdempotentKey.genCancelKey(), getPayload(dbIdempotentKey));
            } else {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 Inbox 이벤트 타입입니다. type=" + event.getEventName());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Inbox 이벤트 처리 중 오류가 발생했습니다.", e);
        }
    }

    private List<IncreaseStockCommand> getPayload(DbIdempotentKey dbIdempotentKey) throws JsonProcessingException {
        return objectMapper.readValue(dbIdempotentKey.getPayload(), new TypeReference<>() {});
    }
}
