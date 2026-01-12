package com.jumunhasyeo.hub.hub.infrastructure.dynamic;

import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.application.command.DeleteHubCommand;
import com.jumunhasyeo.hub.hub.application.command.UpdateHubCommand;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 동적 캐시 스위칭용 HubService 프록시
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "dynamic.enabled", havingValue = "true")
public class DynamicHubServiceProxy implements HubService {
    
    private final HubDynamicConfig config;
    private final Map<String, HubService> implementations;
    
    public DynamicHubServiceProxy(
            HubDynamicConfig config,
            List<HubService> hubServices
    ) {
        this.config = config;
        this.implementations = new java.util.HashMap<>();
        
        for (HubService service : hubServices) {
            if (service == this) continue;
            
            String className = service.getClass().getSimpleName();
            if (className.contains("Redis")) {
                implementations.put("REDIS", service);
            } else if (className.equals("HubServiceImpl")) {
                implementations.put("NONE", service);
            }
        }
        
        log.info("[Dynamic] HubService Proxy initialized: {}", implementations.keySet());
    }
    
    private HubService resolve() {
        String active = config.getHubCache().toUpperCase();
        HubService impl = implementations.get(active);
        
        if (impl == null) {
            log.warn("[Dynamic] Unknown type '{}', fallback to REDIS", active);
            impl = implementations.get("REDIS");
        }
        
        return impl;
    }
    
    @Override
    public HubRes create(CreateHubCommand command) {
        return resolve().create(command);
    }
    
    @Override
    public HubRes update(UpdateHubCommand command) {
        return resolve().update(command);
    }
    
    @Override
    public UUID delete(DeleteHubCommand command) {
        return resolve().delete(command);
    }
    
    @Override
    public HubRes getById(UUID hubId) {
        return resolve().getById(hubId);
    }
    
    @Override
    public Page<HubRes> search(HubSearchCondition condition, Pageable pageable) {
        return resolve().search(condition, pageable);
    }
    
    @Override
    public Boolean existById(UUID hubId) {
        return resolve().existById(hubId);
    }
    
    @Override
    public List<HubRes> getAll() {
        return resolve().getAll();
    }
}
