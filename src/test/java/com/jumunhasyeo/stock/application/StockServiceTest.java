package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.CreateStockCommand;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.DeleteStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.command.ShippedStockCommand;
import com.jumunhasyeo.stock.application.command.StoreStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockHistoryRes;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.application.service.HubClient;
import com.jumunhasyeo.stock.application.service.ProductClient;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockService 단위 테스트")
class StockServiceTest {

    @Mock
    private StockVariationService stockVariationService;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockHistoryRepository stockHistoryRepository;
    @Mock
    private HubClient hubClient;
    @Mock
    private ProductClient productClient;

    @InjectMocks
    private StockService stockService;

    @Test
    @DisplayName("재고를 생성할 수 있다.")
    void create_stock_success() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Integer quantity = 100;
        CreateStockCommand command = new CreateStockCommand(hubId, productId, quantity);

        Stock stock = Stock.builder()
                .stockId(UUID.randomUUID())
                .hubId(hubId)
                .productId(productId)
                .quantity(quantity)
                .build();

        given(hubClient.existHub(hubId)).willReturn(true);
        given(productClient.existProduct(productId)).willReturn(true);
        given(stockRepository.save(any(Stock.class))).willReturn(stock);

        // when
        StockRes result = stockService.create(command);

        // then
        assertThat(result.stockId()).isEqualTo(stock.getStockId());
        assertThat(result.hubId()).isEqualTo(hubId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.quantity()).isEqualTo(quantity);
    }

    @Test
    @DisplayName("허브가 존재하지 않으면 재고 생성에 실패한다.")
    void create_stock_fail_when_hub_not_exist() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CreateStockCommand command = new CreateStockCommand(hubId, productId, 100);

        given(hubClient.existHub(hubId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> stockService.create(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("허브 또는 상품이 존재하지 않습니다.");
    }

    @Test
    @DisplayName("상품이 존재하지 않으면 재고 생성에 실패한다.")
    void create_stock_fail_when_product_not_exist() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CreateStockCommand command = new CreateStockCommand(hubId, productId, 100);

        given(hubClient.existHub(hubId)).willReturn(true);
        given(productClient.existProduct(productId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> stockService.create(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("허브 또는 상품이 존재하지 않습니다.");
    }

    @Test
    @DisplayName("재고 ID로 재고를 조회할 수 있다.")
    void get_stock_success() {
        // given
        UUID stockId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = Stock.builder()
                .stockId(stockId)
                .hubId(hubId)
                .productId(productId)
                .quantity(100)
                .build();

        given(stockRepository.findById(stockId)).willReturn(Optional.of(stock));

        // when
        StockRes result = stockService.get(stockId);

        // then
        assertThat(result.stockId()).isEqualTo(stockId);
        assertThat(result.hubId()).isEqualTo(hubId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.quantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("존재하지 않는 재고 ID로 조회 시 예외가 발생한다.")
    void get_stock_fail_when_not_found() {
        // given
        UUID stockId = UUID.randomUUID();
        given(stockRepository.findById(stockId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> stockService.get(stockId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_EXCEPTION);
    }

    @Test
    @DisplayName("재고를 논리적으로 삭제할 수 있다.")
    void delete_stock_success() {
        // given
        UUID stockId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Long userId = 123L;
        DeleteStockCommand command = new DeleteStockCommand(stockId, userId);

        Stock stock = Stock.builder()
                .stockId(stockId)
                .hubId(hubId)
                .productId(productId)
                .quantity(100)
                .build();

        given(stockRepository.findById(stockId)).willReturn(Optional.of(stock));

        // when
        StockRes result = stockService.delete(command);

        // then
        assertThat(result.stockId()).isEqualTo(stockId);
        assertThat(result.deletedBy()).isEqualTo(userId);
        assertThat(result.deletedAt()).isNotNull();
        verify(stockRepository).findById(stockId);
    }

    @Test
    @DisplayName("존재하지 않는 재고를 삭제하려고 하면 예외가 발생한다.")
    void delete_stock_fail_when_not_found() {
        // given
        UUID stockId = UUID.randomUUID();
        Long userId = 123L;
        DeleteStockCommand command = new DeleteStockCommand(stockId, userId);

        given(stockRepository.findById(stockId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> stockService.delete(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_EXCEPTION);
    }

    @Test
    @DisplayName("재고 감소는 productId 순서대로 처리한다.")
    void decrement_should_sort_by_product_id() {
        UUID p3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID hubId = UUID.randomUUID();

        DecreaseStockCommand c3 = new DecreaseStockCommand(p3, 3);
        DecreaseStockCommand c1 = new DecreaseStockCommand(p1, 1);
        DecreaseStockCommand c2 = new DecreaseStockCommand(p2, 2);

        StockRes r1 = StockRes.from(Stock.of(hubId, p1, 99));
        StockRes r2 = StockRes.from(Stock.of(hubId, p2, 98));
        StockRes r3 = StockRes.from(Stock.of(hubId, p3, 97));

        List<DecreaseStockCommand> sortedCommands = List.of(c1, c2, c3);
        given(stockVariationService.decrement(sortedCommands)).willReturn(List.of(r1, r2, r3));

        List<StockRes> result = stockService.decrement("idem-key", List.of(c3, c1, c2));

        assertThat(result).containsExactly(r1, r2, r3);
        verify(stockVariationService).decrement(sortedCommands);
    }

    @Test
    @DisplayName("재고 증가는 productId 순서대로 처리한다.")
    void increment_should_sort_by_product_id() {
        UUID p3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID hubId = UUID.randomUUID();

        IncreaseStockCommand c3 = new IncreaseStockCommand(p3, 3);
        IncreaseStockCommand c1 = new IncreaseStockCommand(p1, 1);
        IncreaseStockCommand c2 = new IncreaseStockCommand(p2, 2);

        StockRes r1 = StockRes.from(Stock.of(hubId, p1, 101));
        StockRes r2 = StockRes.from(Stock.of(hubId, p2, 102));
        StockRes r3 = StockRes.from(Stock.of(hubId, p3, 103));

        List<IncreaseStockCommand> sortedCommands = List.of(c1, c2, c3);
        given(stockVariationService.increment(sortedCommands)).willReturn(List.of(r1, r2, r3));

        List<StockRes> result = stockService.increment("idem-key", List.of(c3, c1, c2));

        assertThat(result).containsExactly(r1, r2, r3);
        verify(stockVariationService).increment(sortedCommands);
    }

    @Test
    @DisplayName("입고 이력 저장 후 StockHistoryRes로 변환한다.")
    void store_should_save_histories_and_return_response() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        StoreStockCommand command = new StoreStockCommand(hubId, productId, 10);

        StockHistory history = StockHistory.ofStore(hubId, productId, 10, "idem-store");
        given(stockHistoryRepository.saveAll(any())).willReturn(List.of(history));

        List<StockHistoryRes> result = stockService.store("idem-store", List.of(command));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hubId()).isEqualTo(hubId);
        assertThat(result.get(0).productId()).isEqualTo(productId);
        assertThat(result.get(0).type()).isEqualTo(StockHistory.StockHistoryType.STORE.name());
        assertThat(result.get(0).quantity()).isEqualTo(10);
        verify(stockHistoryRepository).saveAll(argThat(histories ->
                histories.size() == 1 && "idem-store".equals(histories.get(0).getIdempotencyKey())
        ));
        verifyNoInteractions(stockVariationService);
    }

    @Test
    @DisplayName("출고 이력 저장 후 StockHistoryRes로 변환한다.")
    void shipped_should_save_histories_and_return_response() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ShippedStockCommand command = new ShippedStockCommand(hubId, productId, 5);

        StockHistory history = StockHistory.ofShipped(hubId, productId, 5, "idem-shipped");
        given(stockHistoryRepository.saveAll(any())).willReturn(List.of(history));

        List<StockHistoryRes> result = stockService.shipped("idem-shipped", List.of(command));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hubId()).isEqualTo(hubId);
        assertThat(result.get(0).productId()).isEqualTo(productId);
        assertThat(result.get(0).type()).isEqualTo(StockHistory.StockHistoryType.SHIPPED.name());
        assertThat(result.get(0).quantity()).isEqualTo(5);
        verify(stockHistoryRepository).saveAll(argThat(histories ->
                histories.size() == 1 && "idem-shipped".equals(histories.get(0).getIdempotencyKey())
        ));
        verifyNoInteractions(stockVariationService);
    }

    @Test
    @DisplayName("입고 이력 다건 저장 시 요청 단위 멱등키를 공통으로 저장한다.")
    void store_should_use_same_idempotency_key_for_all_lines() {
        UUID hubId = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        List<StoreStockCommand> commands = List.of(
                new StoreStockCommand(hubId, productId1, 10),
                new StoreStockCommand(hubId, productId2, 20)
        );

        given(stockHistoryRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

        List<StockHistoryRes> result = stockService.store("idem-batch-store", commands);

        assertThat(result).hasSize(2);
        verify(stockHistoryRepository).saveAll(argThat(histories ->
                histories.size() == 2
                        && histories.stream().allMatch(history -> "idem-batch-store".equals(history.getIdempotencyKey()))
        ));
    }

    @Test
    @DisplayName("출고 이력 다건 저장 시 요청 단위 멱등키를 공통으로 저장한다.")
    void shipped_should_use_same_idempotency_key_for_all_lines() {
        UUID hubId = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        List<ShippedStockCommand> commands = List.of(
                new ShippedStockCommand(hubId, productId1, 5),
                new ShippedStockCommand(hubId, productId2, 7)
        );

        given(stockHistoryRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

        List<StockHistoryRes> result = stockService.shipped("idem-batch-shipped", commands);

        assertThat(result).hasSize(2);
        verify(stockHistoryRepository).saveAll(argThat(histories ->
                histories.size() == 2
                        && histories.stream().allMatch(history -> "idem-batch-shipped".equals(history.getIdempotencyKey()))
        ));
        verifyNoInteractions(stockVariationService);
    }
}
