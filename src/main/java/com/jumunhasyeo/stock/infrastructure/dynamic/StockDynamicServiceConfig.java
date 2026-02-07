package com.jumunhasyeo.stock.infrastructure.dynamic;

import com.jumunhasyeo.stock.application.StockVariationServiceImpl;
import com.jumunhasyeo.stock.application.StockVariationServicePessimisticLock;
import com.jumunhasyeo.stock.domain.repository.StockHistoryRepository;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "true")
public class StockDynamicServiceConfig {

    @Bean
    @Qualifier("stockVariationStrategy")
    public StockVariationServiceImpl stockVariationServiceImpl(
            StockRepository stockRepository,
            StockHistoryRepository stockHistoryRepository,
            EntityManager entityManager
    ) {
        log.info("[Dynamic] Creating StockVariationServiceImpl");
        return new StockVariationServiceImpl(stockRepository, stockHistoryRepository, entityManager);
    }

    @Bean
    @Qualifier("stockVariationStrategy")
    public StockVariationServicePessimisticLock stockVariationServicePessimisticLock(
            StockRepository stockRepository,
            StockHistoryRepository stockHistoryRepository
    ) {
        log.info("[Dynamic] Creating StockVariationServicePessimisticLock");
        return new StockVariationServicePessimisticLock(stockRepository, stockHistoryRepository);
    }
}
