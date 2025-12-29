package com.jumunhasyeo.common.dynamic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * 동적 구현체 전환 API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dynamic") //TODO: FIX 테스트를 위해 internal 제거
@RequiredArgsConstructor
public class SwitchController {
    
    private final DynamicConfig config;
    
    private static final Set<String> VALID_HUB_TYPES = Set.of("REDIS", "NONE");
    private static final Set<String> VALID_STOCK_TYPES = Set.of("DEFAULT", "PESSIMISTIC_LOCK");
    
    /**
     * 현재 설정 조회
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> getCurrent() {
        return ResponseEntity.ok(Map.of(
                "hubCache", config.getHubCache(),
                "stockLock", config.getStockLock()
        ));
    }
    
    /**
     * HubService 캐시 타입 전환
     * 
     * PUT /internal/api/v1/dynamic/hub?type=REDIS
     */
    @PutMapping("/hub")
    public ResponseEntity<Map<String, Object>> switchHubCache(@RequestParam String type) {
        String normalized = type.toUpperCase();
        
        if (!VALID_HUB_TYPES.contains(normalized)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid type. Must be: " + VALID_HUB_TYPES
            ));
        }
        
        String previous = config.getHubCache();
        config.setHubCache(normalized);
        
        log.info("[Dynamic] HubCache: {} -> {}", previous, normalized);
        
        return ResponseEntity.ok(Map.of(
                "service", "HubService",
                "previous", previous,
                "current", normalized
        ));
    }
    
    /**
     * StockVariationService 락 타입 전환
     * 
     * PUT /internal/api/v1/dynamic/stock?type=PESSIMISTIC_LOCK
     */
    @PutMapping("/stock")
    public ResponseEntity<Map<String, Object>> switchStockLock(@RequestParam String type) {
        String normalized = type.toUpperCase();
        
        if (!VALID_STOCK_TYPES.contains(normalized)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid type. Must be: " + VALID_STOCK_TYPES
            ));
        }
        
        String previous = config.getStockLock();
        config.setStockLock(normalized);
        
        log.info("[Dynamic] StockLock: {} -> {}", previous, normalized);
        
        return ResponseEntity.ok(Map.of(
                "service", "StockVariationService",
                "previous", previous,
                "current", normalized
        ));
    }
}
