package com.jumunhasyeo.stock.infrastructure.event;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.stock.application.StockService;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jumunhasyeo.common.exception.ErrorCode.SUCCESS_CONFLICT_EXCEPTION;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaStockCompensationService {

    private final StockService stockService;

    public void incrementCompensation(String compensationKey, List<IncreaseStockCommand> commandList) {
        try {
            stockService.increment(compensationKey, commandList);
        } catch (BusinessException e) {
            if (SUCCESS_CONFLICT_EXCEPTION.equals(e.getErrorCode())) {
                log.info("Ignore already completed stock compensation. key={}", compensationKey);
                return;
            }
            throw e;
        }
    }
}
