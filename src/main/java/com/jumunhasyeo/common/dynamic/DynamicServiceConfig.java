package com.jumunhasyeo.common.dynamic;

import com.jumunhasyeo.hub.hub.application.HubEventPublisher;
import com.jumunhasyeo.hub.hub.application.HubRedisCachedDecoratorService;
import com.jumunhasyeo.hub.hub.application.HubServiceImpl;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepositoryCustom;
import com.jumunhasyeo.stock.application.StockVariationServiceImpl;
import com.jumunhasyeo.stock.application.StockVariationServicePessimisticLock;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 동적 모드용 빈 설정
 * 
 * dynamic.enabled=true 일 때 모든 구현체를 빈으로 등록
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "true")
public class DynamicServiceConfig {
    
    // ==================== HubService ====================
    
    @Bean
    public HubServiceImpl hubServiceImpl(
            HubRepository hubRepository,
            HubRepositoryCustom hubRepositoryCustom,
            HubEventPublisher hubEventPublisher
    ) {
        log.info("[Dynamic] Creating HubServiceImpl");
        return new HubServiceImpl(hubRepository, hubRepositoryCustom, hubEventPublisher);
    }
    
    @Bean
    public HubRedisCachedDecoratorService hubRedisCached(HubServiceImpl hubServiceImpl) {
        log.info("[Dynamic] Creating HubRedisCachedDecoratorService");
        return new HubRedisCachedDecoratorService(hubServiceImpl);
    }
    
    // ==================== StockVariationService ====================
    
    @Bean
    @Qualifier("stockVariationStrategy")
    public StockVariationServiceImpl stockVariationServiceImpl(
            StockRepository stockRepository,
            EntityManager entityManager
    ) {
        log.info("[Dynamic] Creating StockVariationServiceImpl");
        return new StockVariationServiceImpl(stockRepository, entityManager);
    }
    
    @Bean
    @Qualifier("stockVariationStrategy")
    public StockVariationServicePessimisticLock stockVariationServicePessimisticLock(
            StockRepository stockRepository
    ) {
        log.info("[Dynamic] Creating StockVariationServicePessimisticLock");
        return new StockVariationServicePessimisticLock(stockRepository);
    }
}
