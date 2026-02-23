package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.stock.application.command.CreateStockCommand;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.DeleteStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.application.service.HubClient;
import com.jumunhasyeo.stock.application.service.ProductClient;
import com.jumunhasyeo.stock.domain.entity.Stock;
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

        DecreaseStockCommand c3 = new DecreaseStockCommand(hubId, p3, 3);
        DecreaseStockCommand c1 = new DecreaseStockCommand(hubId, p1, 1);
        DecreaseStockCommand c2 = new DecreaseStockCommand(hubId, p2, 2);

        StockChangeRes r1 = StockChangeRes.decrease(hubId, p1, 1);
        StockChangeRes r2 = StockChangeRes.decrease(hubId, p2, 2);
        StockChangeRes r3 = StockChangeRes.decrease(hubId, p3, 3);

        List<DecreaseStockCommand> sortedCommands = List.of(c1, c2, c3);
        given(stockVariationService.decrement("idem-key", sortedCommands)).willReturn(List.of(r1, r2, r3));

        List<StockChangeRes> result = stockService.decrement("idem-key", List.of(c3, c1, c2));

        assertThat(result).containsExactly(r1, r2, r3);
        verify(stockVariationService).decrement("idem-key", sortedCommands);
    }

    @Test
    @DisplayName("재고 감소 요청에 중복 상품이 있으면 예외가 발생한다.")
    void decrement_should_fail_when_product_duplicated() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        List<DecreaseStockCommand> commands = List.of(
                new DecreaseStockCommand(hubId, productId, 3),
                new DecreaseStockCommand(hubId, productId, 5)
        );

        assertThatThrownBy(() -> stockService.decrement("idem-key", commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT)
                .hasMessageContaining("중복된 허브 상품 재고 감소 요청입니다.");

        verifyNoInteractions(stockVariationService);
    }

    @Test
    @DisplayName("재고 감소 수량이 0 이하이면 변경 서비스 호출 전에 예외가 발생한다.")
    void decrement_should_fail_when_amount_not_positive() {
        List<DecreaseStockCommand> commands = List.of(
                new DecreaseStockCommand(UUID.randomUUID(), UUID.randomUUID(), 0)
        );

        assertThatThrownBy(() -> stockService.decrement("idem-key", commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STOCK_VALID)
                .hasMessageContaining("재고 변경 수량은 0보다 커야 합니다.");

        verifyNoInteractions(stockVariationService);
    }

    @Test
    @DisplayName("재고 감소 요청은 상품이 같아도 허브가 다르면 중복이 아니다.")
    void decrement_should_allow_same_product_in_different_hubs() {
        UUID hub1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID hub2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID productId = UUID.randomUUID();

        DecreaseStockCommand c1 = new DecreaseStockCommand(hub1, productId, 3);
        DecreaseStockCommand c2 = new DecreaseStockCommand(hub2, productId, 5);
        StockChangeRes r1 = StockChangeRes.decrease(hub1, productId, 3);
        StockChangeRes r2 = StockChangeRes.decrease(hub2, productId, 5);

        given(stockVariationService.decrement("idem-key", List.of(c1, c2))).willReturn(List.of(r1, r2));

        List<StockChangeRes> result = stockService.decrement("idem-key", List.of(c2, c1));

        assertThat(result).containsExactly(r1, r2);
        verify(stockVariationService).decrement("idem-key", List.of(c1, c2));
    }

    @Test
    @DisplayName("재고 증가는 productId 순서대로 처리한다.")
    void increment_should_sort_by_product_id() {
        UUID p3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID hubId = UUID.randomUUID();

        IncreaseStockCommand c3 = new IncreaseStockCommand(hubId, p3, 3);
        IncreaseStockCommand c1 = new IncreaseStockCommand(hubId, p1, 1);
        IncreaseStockCommand c2 = new IncreaseStockCommand(hubId, p2, 2);

        StockChangeRes r1 = StockChangeRes.increase(hubId, p1, 1);
        StockChangeRes r2 = StockChangeRes.increase(hubId, p2, 2);
        StockChangeRes r3 = StockChangeRes.increase(hubId, p3, 3);

        List<IncreaseStockCommand> sortedCommands = List.of(c1, c2, c3);
        given(stockVariationService.increment("idem-key", sortedCommands)).willReturn(List.of(r1, r2, r3));

        List<StockChangeRes> result = stockService.increment("idem-key", List.of(c3, c1, c2));

        assertThat(result).containsExactly(r1, r2, r3);
        verify(stockVariationService).increment("idem-key", sortedCommands);
    }

    @Test
    @DisplayName("재고 증가 요청에 중복 상품이 있으면 예외가 발생한다.")
    void increment_should_fail_when_product_duplicated() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        List<IncreaseStockCommand> commands = List.of(
                new IncreaseStockCommand(hubId, productId, 3),
                new IncreaseStockCommand(hubId, productId, 5)
        );

        assertThatThrownBy(() -> stockService.increment("idem-key", commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT)
                .hasMessageContaining("중복된 허브 상품 재고 증가 요청입니다.");

        verifyNoInteractions(stockVariationService);
    }

    @Test
    @DisplayName("재고 증가 수량이 0 이하이면 변경 서비스 호출 전에 예외가 발생한다.")
    void increment_should_fail_when_amount_not_positive() {
        List<IncreaseStockCommand> commands = List.of(
                new IncreaseStockCommand(UUID.randomUUID(), UUID.randomUUID(), 0)
        );

        assertThatThrownBy(() -> stockService.increment("idem-key", commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STOCK_VALID)
                .hasMessageContaining("재고 변경 수량은 0보다 커야 합니다.");

        verifyNoInteractions(stockVariationService);
    }

    @Test
    @DisplayName("재고 증가 요청은 상품이 같아도 허브가 다르면 중복이 아니다.")
    void increment_should_allow_same_product_in_different_hubs() {
        UUID hub1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID hub2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID productId = UUID.randomUUID();

        IncreaseStockCommand c1 = new IncreaseStockCommand(hub1, productId, 3);
        IncreaseStockCommand c2 = new IncreaseStockCommand(hub2, productId, 5);
        StockChangeRes r1 = StockChangeRes.increase(hub1, productId, 3);
        StockChangeRes r2 = StockChangeRes.increase(hub2, productId, 5);

        given(stockVariationService.increment("idem-key", List.of(c1, c2))).willReturn(List.of(r1, r2));

        List<StockChangeRes> result = stockService.increment("idem-key", List.of(c2, c1));

        assertThat(result).containsExactly(r1, r2);
        verify(stockVariationService).increment("idem-key", List.of(c1, c2));
    }
}
