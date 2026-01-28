package com.jumunhasyeo.stock.infrastructure.repository;

import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.testsupport.RepositorySliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaStockHistoryRepositorySliceTest extends RepositorySliceTest {

    @Autowired
    private JpaStockHistoryRepository jpaStockHistoryRepository;

    @Test
    @DisplayName("동일 요청 키를 가진 재고 이력 다건을 저장할 수 있다.")
    void saveAll_with_same_idempotency_key_success() {
        UUID hubId = UUID.randomUUID();
        String idempotencyKey = "idem-request-level";

        StockHistory history1 = StockHistory.ofStore(hubId, UUID.randomUUID(), 10, idempotencyKey);
        StockHistory history2 = StockHistory.ofStore(hubId, UUID.randomUUID(), 20, idempotencyKey);

        List<StockHistory> saved = jpaStockHistoryRepository.saveAll(List.of(history1, history2));
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(saved).hasSize(2);
        assertThat(jpaStockHistoryRepository.count()).isEqualTo(2);
    }
}
