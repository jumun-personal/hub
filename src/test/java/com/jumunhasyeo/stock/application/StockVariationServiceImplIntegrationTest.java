package com.jumunhasyeo.stock.application;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.stock.application.command.DecreaseStockCommand;
import com.jumunhasyeo.stock.application.command.IncreaseStockCommand;
import com.jumunhasyeo.stock.application.dto.response.StockRes;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.testsupport.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class StockVariationServiceImplIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private StockVariationServiceImpl stockService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("decreaseStock() 실행시 변경 감지를 차단해 중복 쿼리가 발생되지 않는다.")
    public void decreaseStock_실행시_변경_감지를_차단해_중복_쿼리가_발생되지_않는다() {
        //given
        UUID productId = UUID.randomUUID();
        Stock stock = transactionTemplate.execute(status -> {
            return createSaveStock(productId, 500);
        });
        DecreaseStockCommand command = new DecreaseStockCommand(stock.getProductId(), 100);

        // 쿼리 발생 횟수 검증을 위한 Hibernate Statistics 초기화
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        //when
        StockRes stockRes = transactionTemplate.execute(status -> stockService.decrement(command));
        //then
        assertThat(stockRes.stockId()).isEqualTo(stock.getStockId());
        assertThat(stockRes.quantity()).isEqualTo(400);
        assertThat(stockRes.hubId()).isEqualTo(stock.getHubId());
        assertThat(stockRes.productId()).isEqualTo(stock.getProductId());
        assertThat(statistics.getQueryExecutionCount()) //  UPDATE 쿼리가 1번만 발생했는지 검증
                .as("UPDATE 쿼리는 1번만 발생해야 합니다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("increaseStock() 실행시 변경 감지를 차단해 중복 쿼리가 발생되지 않는다.")
    public void increaseStock_실행시_변경_감지를_차단해_중복_쿼리가_발생되지_않는다() {
        //given
        UUID productId = UUID.randomUUID();
        Stock stock = transactionTemplate.execute(status -> {
            return createSaveStock(productId, 500);
        });
        IncreaseStockCommand command = new IncreaseStockCommand(stock.getProductId(), 100);

        // 쿼리 발생 횟수 검증을 위한 Hibernate Statistics 초기화
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        //when
        StockRes stockRes = transactionTemplate.execute(status -> stockService.increment(command));
        //then
        assertThat(stockRes.stockId()).isEqualTo(stock.getStockId());
        assertThat(stockRes.quantity()).isEqualTo(600);
        assertThat(stockRes.hubId()).isEqualTo(stock.getHubId());
        assertThat(stockRes.productId()).isEqualTo(stock.getProductId());
        assertThat(statistics.getQueryExecutionCount()) //  UPDATE 쿼리가 1번만 발생했는지 검증
                .as("UPDATE 쿼리는 1번만 발생해야 합니다")
                .isEqualTo(1);
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
