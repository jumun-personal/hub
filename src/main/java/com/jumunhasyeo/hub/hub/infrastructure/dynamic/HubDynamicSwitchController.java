package com.jumunhasyeo.hub.hub.infrastructure.dynamic;

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
@RequestMapping("/internal/api/v1/dynamic/hub")
@RequiredArgsConstructor
public class HubDynamicSwitchController {

    private static final Set<String> VALID_TYPES = Set.of("REDIS", "NONE");

    private final HubDynamicConfig config;

    @GetMapping
    public ResponseEntity<Map<String, String>> getCurrent() {
        return ResponseEntity.ok(Map.of("hubCache", config.getHubCache()));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> switchHubCache(@RequestParam String type) {
        String normalized = type.toUpperCase();
        if (!VALID_TYPES.contains(normalized)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid type. Must be: " + VALID_TYPES));
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
}
