package com.jumunhasyeo.common.init;

import com.jumunhasyeo.stock.domain.entity.Stock;
import com.jumunhasyeo.stock.domain.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Profile("!test")
public class BlackFridayStockPreloadRunner implements ApplicationRunner {

    @Value("${init.bf.preload.enabled:false}")
    private String preloadEnabled;

    private final RedisTemplate<String, Object> bfRedisTemplate;
    private final StockRepository stockRepository;

    public BlackFridayStockPreloadRunner(
            @Qualifier("bfRedisTemplate") RedisTemplate<String, Object> bfRedisTemplate,
            StockRepository stockRepository
    ) {
        this.bfRedisTemplate = bfRedisTemplate;
        this.stockRepository = stockRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if("TRUE".equalsIgnoreCase(preloadEnabled)) {
            int BATCH_SIZE = 5_000;
            UUID lastId = null;
            LocalDateTime lastCreatedAt = LocalDateTime.MIN;

            while (true) {
                Pageable pageable = PageRequest.of(0, BATCH_SIZE);
                List<Stock> stocks = stockRepository.findNextBatch(
                        lastCreatedAt, lastId, pageable
                );
                if (stocks.isEmpty()) break;

                preloadBatch(stocks);

                Stock lastStock = stocks.get(stocks.size() - 1);
                lastCreatedAt = lastStock.getCreatedAt();
                lastId = lastStock.getStockId();
            }
        }
    }

    private void preloadBatch(List<Stock> stocks) {
        bfRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Stock stock : stocks) {
                String key = "bf:stock:" + stock.getProductId();
                byte[] k = bfRedisTemplate.getStringSerializer().serialize(key);
                byte[] v = String.valueOf(stock.getQuantity()).getBytes(StandardCharsets.UTF_8);
                connection.set(k, v);
            }
            return null;
        });

    }
}
