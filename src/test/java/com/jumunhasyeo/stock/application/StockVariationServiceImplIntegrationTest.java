package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockChangeRes;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.entity.StockHistory;
import com.jumunhasyeo.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class StockVariationServiceImplIntegrationTest extends IntegrationTest {
    @Autowired
    private StockVariationServiceImpl stockService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("decrement()는 조건부 UPDATE와 이력 INSERT를 각각 한 번 실행한다.")
    public void decreaseStock_실행시_변경_감지를_차단해_중복_쿼리가_발생되지_않는다() {
        //given
        UUID productId = UUID.randomUUID();
        Stock stock = transactionTemplate.execute(status -> {
            return createSaveStock(productId, 500);
        });
        DecreaseStockCommand command = new DecreaseStockCommand(stock.getHubId(), stock.getProductId(), 100);

        // 쿼리 발생 횟수 검증을 위한 Hibernate Statistics 초기화
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        //when
        StockChangeRes stockChangeRes = transactionTemplate.execute(status -> stockService.decrement("idem-decrease", List.of(command)).get(0));
        //then
        assertThat(stockChangeRes.quantity()).isEqualTo(100);
        assertThat(stockChangeRes.type()).isEqualTo(StockHistory.StockHistoryType.DECREASE);
        assertThat(stockChangeRes.hubId()).isEqualTo(stock.getHubId());
        assertThat(stockChangeRes.productId()).isEqualTo(stock.getProductId());
        assertThat(statistics.getPrepareStatementCount())
                .as("조건부 UPDATE와 재고 이력 INSERT만 실행해야 합니다")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("increment()는 조건부 UPDATE와 이력 INSERT를 각각 한 번 실행한다.")
    public void increaseStock_실행시_변경_감지를_차단해_중복_쿼리가_발생되지_않는다() {
        //given
        UUID productId = UUID.randomUUID();
        Stock stock = transactionTemplate.execute(status -> {
            return createSaveStock(productId, 500);
        });
        IncreaseStockCommand command = new IncreaseStockCommand(stock.getHubId(), stock.getProductId(), 100);

        // 쿼리 발생 횟수 검증을 위한 Hibernate Statistics 초기화
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        //when
        StockChangeRes stockChangeRes = transactionTemplate.execute(status -> stockService.increment("idem-increase", List.of(command)).get(0));
        //then
        assertThat(stockChangeRes.quantity()).isEqualTo(100);
        assertThat(stockChangeRes.type()).isEqualTo(StockHistory.StockHistoryType.INCREASE);
        assertThat(stockChangeRes.hubId()).isEqualTo(stock.getHubId());
        assertThat(stockChangeRes.productId()).isEqualTo(stock.getProductId());
        assertThat(statistics.getPrepareStatementCount())
                .as("조건부 UPDATE와 재고 이력 INSERT만 실행해야 합니다")
                .isEqualTo(2);
    }

    private Stock createSaveStock(UUID productId, int quantity) {
        Hub hub = createHub();
        entityManager.persist(hub);
        Stock stock = Stock.builder()
                .productId(productId)
                .quantity(quantity)
                .hubId(hub.getHubId())
                .build();
        entityManager.persist(stock);
        entityManager.flush();
        return stock;
    }

    private  Hub createHub() {
        return Hub.builder()
                .name("송파 허브")
                .status(HubStatus.COMPLETE)
                .address(Address.of("street", Coordinate.of(12.6, 12.6)))
                .build();
    }
}
