package com.jumunhasyeo.stock.infrastructure.repository;

import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.testsupport.RepositorySliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaStockHistoryRepositorySliceTest extends RepositorySliceTest {

    @Autowired
    private JpaStockHistoryRepository jpaStockHistoryRepository;

    @Test
    @DisplayName("동일 요청 키를 가진 재고 이력 다건을 저장할 수 있다.")
    void saveAll_with_same_idempotency_key_success() {
        UUID hubId = UUID.randomUUID();
        String idempotencyKey = "idem-request-level";

        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        StockHistory history1 = StockHistory.ofDecrease(hubId, productId1, 10, idempotencyKey);
        StockHistory history2 = StockHistory.ofDecrease(hubId, productId2, 20, idempotencyKey);

        List<StockHistory> saved = jpaStockHistoryRepository.saveAll(List.of(history1, history2));
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(saved).hasSize(2);
        assertThat(jpaStockHistoryRepository.count()).isEqualTo(2);
        assertThat(jpaStockHistoryRepository.findByIdempotencyKeyAndType(idempotencyKey, StockHistory.StockHistoryType.DECREASE))
                .extracting(StockHistory::getProductId)
                .containsExactlyInAnyOrder(productId1, productId2);
    }

    @Test
    @DisplayName("동일 멱등키, 상품, 타입의 재고 이력은 중복 저장할 수 없다.")
    void duplicate_idempotency_product_type_throws() {
        UUID hubId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String idempotencyKey = "idem-duplicate";

        StockHistory history1 = StockHistory.ofDecrease(hubId, productId, 10, idempotencyKey);
        StockHistory history2 = StockHistory.ofDecrease(hubId, productId, 10, idempotencyKey);

        jpaStockHistoryRepository.save(history1);
        jpaStockHistoryRepository.save(history2);

        assertThatThrownBy(() -> testEntityManager.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
