package com.jumunhasyeo.stock.infrastructure.event;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.StockService;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KafkaStockCompensationServiceTest {

    @Mock
    private StockService stockService;

    @InjectMocks
    private KafkaStockCompensationService kafkaStockCompensationService;

    @Test
    @DisplayName("이미 성공한 중복 보상은 no-op로 흡수한다")
    void incrementCompensation_whenSuccessConflict_ignore() {
        List<IncreaseStockCommand> commands = List.of(new IncreaseStockCommand(UUID.randomUUID(), 3));
        given(stockService.increment("cancel-key", commands))
                .willThrow(new BusinessException(ErrorCode.SUCCESS_CONFLICT_EXCEPTION));

        assertThatCode(() -> kafkaStockCompensationService.incrementCompensation("cancel-key", commands))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PROCESSING 충돌은 그대로 전파한다")
    void incrementCompensation_whenProcessingConflict_propagate() {
        List<IncreaseStockCommand> commands = List.of(new IncreaseStockCommand(UUID.randomUUID(), 3));
        given(stockService.increment("cancel-key", commands))
                .willThrow(new BusinessException(ErrorCode.PROCESSING_CONFLICT_EXCEPTION));

        assertThatThrownBy(() -> kafkaStockCompensationService.incrementCompensation("cancel-key", commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROCESSING_CONFLICT_EXCEPTION);
    }
}
