package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.stock.infrastructure.dynamic.StockLockType;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import jakarta.persistence.EntityManager;
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
    @Mock
    private EntityManager entityManager;
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
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 500);
        DecreaseStockCommand command = new DecreaseStockCommand(productId, 100);
        when(stockRepository.findByProductId(any(UUID.class))).thenReturn(Optional.of(stock));
        when(stockRepository.decreaseStock(any(UUID.class), anyInt())).thenReturn(true);
        //when
        StockRes stockRes = stockService.decrement(command);
        //then
        assertThat(stockRes.stockId()).isEqualTo(stock.getStockId());
        assertThat(stockRes.quantity()).isEqualTo(400);
    }

    @Test
    @DisplayName("hub에 상품재고를 증가시킬 수 있다.")
    public void increaseStock_Hub_Success() {
        //given
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 500);
        IncreaseStockCommand command = new IncreaseStockCommand(productId, 100);
        when(stockRepository.findByProductId(any(UUID.class))).thenReturn(Optional.of(stock));
        when(stockRepository.increaseStock(any(UUID.class), anyInt())).thenReturn(true);
        //when
        StockRes stockRes = stockService.increment(command);
        //then
        assertThat(stockRes.stockId()).isEqualTo(stock.getStockId());
        assertThat(stockRes.quantity()).isEqualTo(600);
    }

    @Test
    @DisplayName("재고 감소 리스트 처리 시 감소 이력을 저장한다.")
    void decrement_list_saves_histories() {
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 500);
        DecreaseStockCommand command = new DecreaseStockCommand(productId, 100);
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        when(stockRepository.decreaseStock(stock.getStockId(), 100)).thenReturn(true);

        stockService.decrement("idem-decrease", List.of(command));

        verify(stockHistoryRepository).saveAll(argThat(histories ->
                histories.size() == 1
                        && histories.get(0).getType().equals(StockHistory.StockHistoryType.DECREASE)
                        && histories.get(0).getProductId().equals(productId)
                        && histories.get(0).getQuantity() == 100
                        && histories.get(0).getIdempotencyKey().equals("idem-decrease")
        ));
    }

    @Test
    @DisplayName("재고 증가 리스트 처리 시 증가 이력을 저장한다.")
    void increment_list_saves_histories() {
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 500);
        IncreaseStockCommand command = new IncreaseStockCommand(productId, 100);
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        when(stockRepository.increaseStock(stock.getStockId(), 100)).thenReturn(true);

        stockService.increment("idem-increase", List.of(command));

        verify(stockHistoryRepository).saveAll(argThat(histories ->
                histories.size() == 1
                        && histories.get(0).getType().equals(StockHistory.StockHistoryType.INCREASE)
                        && histories.get(0).getProductId().equals(productId)
                        && histories.get(0).getQuantity() == 100
                        && histories.get(0).getIdempotencyKey().equals("idem-increase")
        ));
    }

    private Stock createStock(UUID productId, int quantity) {
        Hub hub = createHub();
        Stock stock = Stock.builder()
                .stockId(UUID.randomUUID())
                .hubId(hub.getHubId())
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
