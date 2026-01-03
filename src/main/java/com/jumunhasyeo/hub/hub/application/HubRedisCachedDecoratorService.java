package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.application.command.DeleteHubCommand;
import com.jumunhasyeo.hub.hub.application.command.UpdateHubCommand;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
public class HubRedisCachedDecoratorService implements HubService {

    private static final String CACHE_NAME = "hub";

    private final HubService hubService;

    public HubRedisCachedDecoratorService(HubService hubService) {
        this.hubService = hubService;
    }

    /**
     * Hub 생성 후 캐시에 저장
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "'all'", beforeInvocation = false)
    public HubRes create(CreateHubCommand command) {
        HubRes created = hubService.create(command);
        log.info(" Hub Created - hubId: {}", created.id());
        return created;
    }

    /**
     * Hub 수정 후 캐시 업데이트
     */
    @Transactional
    @CachePut(value = CACHE_NAME, key = "#command.hubId()", condition = "#result != null")
    @CacheEvict(value = CACHE_NAME, key = "'all'", beforeInvocation = false)
    public HubRes update(UpdateHubCommand command) {
        HubRes updated = hubService.update(command);
        log.info("Hub Updated & Cached - hubId: {}", updated.id());
        return updated;
    }

    /**
     * Hub 삭제 시 캐시 제거
     */
    @Transactional
    @Caching(
            evict = {
                    @CacheEvict(value = "hub", key = "#command.hubId()", beforeInvocation = false),
                    @CacheEvict(value = "hub", key = "'all'", beforeInvocation = false)
            }
    )
    public UUID delete(DeleteHubCommand command) {
        UUID deletedId = hubService.delete(command);
        log.info(" Hub Deleted & Cache Evicted - hubId: {}", deletedId);
        return deletedId;
    }

    /**
     * Hub 단건 조회
     */
    @Cacheable(value = CACHE_NAME, key = "#hubId", unless = "#result == null")
    public HubRes getById(UUID hubId) {
        log.info(" DB 조회 - hubId: {}", hubId);
        return hubService.getById(hubId);
    }

    /**
     * Hub 검색 - 캐시 사용 안 함
     */
    public Page<HubRes> search(HubSearchCondition condition, Pageable pageable) {
        return hubService.search(condition, pageable);
    }

    @Override
    public Boolean existById(UUID hubId) {
        return hubService.existById(hubId);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'all'", unless = "#result == null or #result.isEmpty()")
    public List<HubRes> getAll() {
        return hubService.getAll();
    }

}
