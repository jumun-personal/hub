package com.jumunhasyeo.stock.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class JpaStockRepositoryConcurrencyTest extends IntegrationTest {

    @Autowired
    private JpaStockRepository jpaStockRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("재고 감소 동시성(원자성) 확인")
    public void decreaseStock_hub_success() throws InterruptedException {
        //given
        UUID productId = UUID.randomUUID();
        int willSuccessCount = 5;
        int initQuantity = willSuccessCount;
        int decreaseRequestCount = 10;
        UUID stockId = transactionTemplate.execute(status -> stockSave(productId, initQuantity));
        //when

        int successCount = executeConcurrently(
                decreaseRequestCount,
                () -> (jpaStockRepository.decreaseStock(stockId, 1) == 1)
        );

        //then
        Stock stock = jpaStockRepository.findByProductId(productId).get(0);
        assertThat(successCount).isEqualTo(willSuccessCount);
        assertThat(stock.getQuantity()).isEqualTo(initQuantity - successCount);
    }

    @Test
    @DisplayName("재고 증가 동시성(원자성) 확인")
    public void inCreaseStock_hub_success() throws InterruptedException {
        //given
        UUID productId = UUID.randomUUID();
        int stockMax = 2147483647;
        int willSuccessCount = 5;
        int initQuantity = stockMax - willSuccessCount;
        int increaseRequestCount = 10;
        UUID stockId = transactionTemplate.execute(status -> stockSave(productId, initQuantity));

        //when
        int successCount = executeConcurrently(
                increaseRequestCount,
                () -> (jpaStockRepository.increaseStock(stockId, 1) == 1)
        );

        //then
        Stock stock = jpaStockRepository.findByProductId(productId).get(0);
        assertThat(successCount).isEqualTo(willSuccessCount);
        assertThat(stock.getQuantity()).isEqualTo(initQuantity + successCount);
    }

    private int executeConcurrently(int threadCount, Supplier<Boolean> supplier) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    if( supplier.get() ){
                        successCount.incrementAndGet();
                    }
                }finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();
        return successCount.get();
    }

    private UUID stockSave(UUID productId, int initQuantity) {
        Hub hub = createHub("송파허브", "송파대로", 12.6, 15.4);
        entityManager.persist(hub);
        entityManager.flush();
        Stock stock = createStock(hub, productId, initQuantity);
        entityManager.persist(stock);
        entityManager.flush();
        return stock.getStockId();
    }

    private Stock createStock(Hub hub, UUID productId, int quantity) {
        return Stock.of(hub.getHubId(), productId, quantity);
    }

    private static Hub createHub(String name, String street, Double lat, Double lon) {
        return Hub.builder()
                .name(name)
                .status(HubStatus.COMPLETE)
                .address(Address.of(street, Coordinate.of(lat, lon)))
                .build();
    }
}
