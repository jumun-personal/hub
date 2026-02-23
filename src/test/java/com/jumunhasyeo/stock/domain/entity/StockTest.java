package com.jumunhasyeo.stock.domain.entity;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 10})
    @DisplayName("재고 수량이 0 이상이면 재고를 생성할 수 있다")
    void of_stock_success(int quantity) {
        // given
        UUID productId = UUID.randomUUID();

        // when
        Stock stock = createStock(productId, quantity);

        // then
        assertThat(stock.getQuantity()).isEqualTo(quantity);
    }

    @Test
    @DisplayName("Stock을 Of로 생성할 때 productId == null일 경우 예외 반환")
    public void of_productIdIsNull_ShouldThrowException() {
        // given
        UUID productid = null;
        Hub hub = Hub.builder().build();
        // when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> Stock.of(hub.getHubId(), productid, 100)
        );
        // then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CREATE_VALIDATE_EXCEPTION);
        assertThat(businessException.getMessage()).contains("객체 생성에 실패했습니다");
    }

    @Test
    @DisplayName("Stock을 Of로 생성할 때 quantity == null일 경우 예외 반환")
    public void of_quantityIsNull_ShouldThrowException() {
        // given
        Integer quantity = null;
        UUID productId = UUID.randomUUID();
        Hub hub = Hub.builder().build();
        // when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> Stock.of(hub.getHubId(), productId, quantity)
        );
        // then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CREATE_VALIDATE_EXCEPTION);
        assertThat(businessException.getMessage()).contains("객체 생성에 실패했습니다");
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, -5, -1})
    @DisplayName("Stock을 Of로 생성할 때 quantity < 0일 경우 예외 반환")
    public void of_quantityLessThanZero_ShouldThrowException(int quantity) {
        // given
        UUID productId = UUID.randomUUID();
        Hub hub = Hub.builder().build();
        // when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> Stock.of(hub.getHubId(), productId, quantity)
        );
        // then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CREATE_VALIDATE_EXCEPTION);
        assertThat(businessException.getMessage()).contains("객체 생성에 실패했습니다");
    }

    @Test
    @DisplayName("재고를 감소시킬 수 있다.")
    public void stock_decrease_success() {
        // given
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 100);
        // when
        stock.decrease(50);
        // then
        assertThat(stock.getQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("재고 변경 수량 양수 검증은 재고 수량을 변경하지 않는다.")
    public void validate_positive_amount_does_not_change_quantity() {
        // given
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 100);
        // when
        stock.validatePositiveAmount(50);
        // then
        assertThat(stock.getQuantity()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    @DisplayName("재고 감소할 때 '감소 값 <= 0'일 경우 예외 반환")
    public void decrease_LessThanZero_ShouldThrowException(int decrease) {
        // given
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 100);
        // when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> stock.decrease(decrease)
        );
        // then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.STOCK_VALID);
        assertThat(businessException.getMessage()).contains("재고 변경 수량은 0보다 커야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {101, 1000, 5000})
    @DisplayName("재고 감소할 때 '감소 값 > 재고 값' 일 경우 예외 반환")
    public void decrease_LessThanQuantity_ShouldThrowException(int decrease) {
        // given
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 100);
        // when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> stock.decrease(decrease)
        );
        // then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.STOCK_VALID);
        assertThat(businessException.getMessage()).contains("재고가 부족합니다.");
    }

    @Test
    @DisplayName("같은 허브 상품에 대한 재고인지 확인할 수 있다.")
    public void isSameHubProduct() {
        // given
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = Stock.of(hubId, productId, 100);
        // when
        boolean isSameProduct = stock.isSameHubProduct(stock.getHubId(), stock.getProductId());
        // then
        assertThat(isSameProduct).isTrue();
    }

    @Test
    @DisplayName("재고를 증가시킬 수 있다.")
    public void increase_stock_success() {
        // given
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 100);
        // when
        stock.increase(50);
        // then
        assertThat(stock.getQuantity()).isEqualTo(150);
    }

    @Test
    @DisplayName("재고가 최대치 초과시 예외 발생")
    public void increase_MaxStock_shouldThrowException() {
        // given
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 100);
        // when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> stock.increase(Integer.MAX_VALUE)
        );
        // then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.STOCK_VALID);
        assertThat(businessException.getMessage()).contains("재고 최대값을 초과했습니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0,-1,-50})
    @DisplayName("재고 증가할 때 '증가 값 <= 0'일 경우 예외 반환")
    public void increase_LessThanZero_ShouldThrowException(int increase) {
        // given
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 100);
        // when
        BusinessException businessException = assertThrows(
                BusinessException.class, () -> stock.increase(increase)
        );
        // then
        assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.STOCK_VALID);
        assertThat(businessException.getMessage()).contains("재고 변경 수량은 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("재고를 정확히 0까지 감소시킬 수 있다")
    public void decrease_ToZero_success() {
        // given
        UUID productId = UUID.randomUUID();
        Stock stock = createStock(productId, 100);
        // when
        stock.decrease(100);
        // then
        assertThat(stock.getQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 0으로 생성 가능")
    public void of_quantityIsZero_success() {
        // given
        UUID productId = UUID.randomUUID();

        // when
        Stock stock = createStock(productId, 0);

        // then
        assertThat(stock.getQuantity()).isEqualTo(0);
    }

    private Stock createStock(UUID productId, int quantity) {
        return Stock.of(createHub().getHubId(), productId, quantity);
    }
    private static Hub createHub() {
        return Hub.builder().hubId(UUID.randomUUID()).build();
    }
}
