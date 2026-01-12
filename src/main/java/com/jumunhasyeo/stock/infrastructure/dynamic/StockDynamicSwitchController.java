package com.jumunhasyeo.stock.infrastructure.dynamic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/internal/api/v1/dynamic/stock")
@RequiredArgsConstructor
public class StockDynamicSwitchController {

    private static final Set<String> VALID_TYPES = Set.of("DEFAULT", "PESSIMISTIC_LOCK");

    private final StockDynamicConfig config;

    @GetMapping
    public ResponseEntity<Map<String, String>> getCurrent() {
        return ResponseEntity.ok(Map.of("stockLock", config.getStockLock()));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> switchStockLock(@RequestParam String type) {
        String normalized = type.toUpperCase();
        if (!VALID_TYPES.contains(normalized)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid type. Must be: " + VALID_TYPES));
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
