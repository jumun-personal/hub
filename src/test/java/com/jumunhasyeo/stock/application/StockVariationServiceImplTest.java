package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.infrastructure.dynamic.StockLockType;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockVariationServiceImplTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockHistoryRepository stockHistoryRepository;
    @InjectMocks
    private StockVariationServiceImpl stockService;

    @Test
    @DisplayName("전략 타입은 DEFAULT이다.")
    void strategy_type_default() {
        assertThat(stockService.type()).isEqualTo(StockLockType.DEFAULT);
    }

    @Test
    @DisplayName("hub에 상품재고를 감소시킬 수 있다.")
    public void decreaseStock_Hub_Success() {
        //given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(hubId, productId, 500);
        DecreaseStockCommand command = new DecreaseStockCommand(hubId, productId, 100);
        when(stockRepository.decreaseStock(any(UUID.class), any(UUID.class), anyInt())).thenReturn(true);
        //when
        StockChangeRes stockChangeRes = stockService.decrement("idem-decrease", List.of(command)).get(0);
        //then
        assertThat(stockChangeRes.hubId()).isEqualTo(stock.getHubId());
        assertThat(stockChangeRes.productId()).isEqualTo(productId);
        assertThat(stockChangeRes.type()).isEqualTo(StockHistory.StockHistoryType.DECREASE);
        assertThat(stockChangeRes.quantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("hub에 상품재고를 증가시킬 수 있다.")
    public void increaseStock_Hub_Success() {
        //given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(hubId, productId, 500);
        IncreaseStockCommand command = new IncreaseStockCommand(hubId, productId, 100);
        when(stockRepository.increaseStock(any(UUID.class), any(UUID.class), anyInt())).thenReturn(true);
        //when
        StockChangeRes stockChangeRes = stockService.increment("idem-increase", List.of(command)).get(0);
        //then
        assertThat(stockChangeRes.hubId()).isEqualTo(stock.getHubId());
        assertThat(stockChangeRes.productId()).isEqualTo(productId);
        assertThat(stockChangeRes.type()).isEqualTo(StockHistory.StockHistoryType.INCREASE);
        assertThat(stockChangeRes.quantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("재고 감소 리스트 처리 시 감소 이력을 저장한다.")
    void decrement_list_saves_histories() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(hubId, productId, 500);
        DecreaseStockCommand command = new DecreaseStockCommand(hubId, productId, 100);
        when(stockRepository.decreaseStock(hubId, productId, 100)).thenReturn(true);

        stockService.decrement("idem-decrease", List.of(command));

        verify(stockHistoryRepository).saveAll(argThat(histories ->
                histories.size() == 1
                        && histories.get(0).getType().equals(StockHistory.StockHistoryType.DECREASE)
                        && histories.get(0).getHubId().equals(hubId)
                        && histories.get(0).getProductId().equals(productId)
                        && histories.get(0).getQuantity() == 100
                        && histories.get(0).getIdempotencyKey().equals("idem-decrease")
        ));
    }

    @Test
    @DisplayName("재고 증가 리스트 처리 시 증가 이력을 저장한다.")
    void increment_list_saves_histories() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(hubId, productId, 500);
        IncreaseStockCommand command = new IncreaseStockCommand(hubId, productId, 100);
        when(stockRepository.increaseStock(hubId, productId, 100)).thenReturn(true);

        stockService.increment("idem-increase", List.of(command));

        verify(stockHistoryRepository).saveAll(argThat(histories ->
                histories.size() == 1
                        && histories.get(0).getType().equals(StockHistory.StockHistoryType.INCREASE)
                        && histories.get(0).getHubId().equals(hubId)
                        && histories.get(0).getProductId().equals(productId)
                        && histories.get(0).getQuantity() == 100
                        && histories.get(0).getIdempotencyKey().equals("idem-increase")
        ));
    }

    @Test
    @DisplayName("조건부 재고 감소가 반영되지 않으면 재고 부족 예외가 발생한다")
    void decrement_whenConditionalUpdateFails_throwsStockNotEnough() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        DecreaseStockCommand command = new DecreaseStockCommand(hubId, productId, 100);
        when(stockRepository.decreaseStock(hubId, productId, 100)).thenReturn(false);

        // when
        var result = assertThatThrownBy(() -> stockService.decrement("idem-decrease", List.of(command)));

        // then
        result.isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STOCK_NOT_ENOUGH);
    }

    @Test
    @DisplayName("조건부 재고 증가가 반영되지 않으면 재고 최대치 초과 예외가 발생한다")
    void increment_whenConditionalUpdateFails_throwsStockMaxExceeded() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        IncreaseStockCommand command = new IncreaseStockCommand(hubId, productId, 100);
        when(stockRepository.increaseStock(hubId, productId, 100)).thenReturn(false);

        // when
        var result = assertThatThrownBy(() -> stockService.increment("idem-increase", List.of(command)));

        // then
        result.isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STOCK_MAX_EXCEEDED);
    }

    private Stock createStock(UUID hubId, UUID productId, int quantity) {
        Stock stock = Stock.builder()
                .stockId(UUID.randomUUID())
                .hubId(hubId)
                .productId(productId)
                .quantity(quantity)
                .build();
        return stock;
    }

    private Hub createHub() {
        return Hub.builder()
                .name("송파 허브")
                .address(Address.of("street", Coordinate.of(12.6, 12.6)))
                .build();

    }
}
