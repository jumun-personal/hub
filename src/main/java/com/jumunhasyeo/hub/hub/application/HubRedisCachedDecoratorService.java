package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.application.command.DeleteHubCommand;
import com.jumunhasyeo.hub.hub.application.command.UpdateHubCommand;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Slf4j
public class HubRedisCachedDecoratorService implements HubService {

    private static final String CACHE_NAME = "hub";
    private static final String CACHE_KEY_PREFIX = CACHE_NAME + "::";
    private static final String LOCK_KEY_PREFIX = "hub:singleflight:lock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """,
            Long.class
    );

    private final HubService hubService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final HubCacheProperties cacheProperties;
    private final Semaphore dbFallbackBulkhead;

    public HubRedisCachedDecoratorService(
            HubService hubService,
            RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            HubCacheProperties cacheProperties
    ) {
        this.hubService = hubService;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheProperties = cacheProperties;
        this.dbFallbackBulkhead = new Semaphore(Math.max(1, cacheProperties.getDbFallbackBulkheadSize()));
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
    public HubRes getById(UUID hubId) {
        String cacheKey = cacheKey(hubId);

        try {
            HubRes cached = getCachedHub(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (DataAccessException e) {
            return fallbackToDbWithBulkhead(hubId, "Redis cache read failed", e);
        }

        return loadWithSingleFlight(hubId, cacheKey);
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

    private HubRes loadWithSingleFlight(UUID hubId, String cacheKey) {
        String lockKey = lockKey(hubId);
        String lockValue = UUID.randomUUID().toString();
        boolean locked;

        try {
            locked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, cacheProperties.getLockTtl()));
        } catch (DataAccessException e) {
            return fallbackToDbWithBulkhead(hubId, "Redis single-flight lock failed", e);
        }

        if (locked) {
            try {
                try {
                    HubRes doubleChecked = getCachedHub(cacheKey);
                    if (doubleChecked != null) {
                        return doubleChecked;
                    }
                } catch (DataAccessException e) {
                    return fallbackToDbWithBulkhead(hubId, "Redis cache double-check failed", e);
                }

                HubRes loaded = hubService.getById(hubId);
                try {
                    cacheHub(cacheKey, loaded);
                } catch (DataAccessException e) {
                    log.warn("Hub DB load succeeded but Redis cache write failed. hubId={}", hubId, e);
                }
                return loaded;
            } finally {
                releaseLock(lockKey, lockValue);
            }
        }

        return waitForCacheOrFallback(hubId, cacheKey);
    }

    private HubRes waitForCacheOrFallback(UUID hubId, String cacheKey) {
        for (int i = 0; i < cacheProperties.getMaxWaitAttempts(); i++) {
            sleepBeforeRetry(hubId);

            try {
                HubRes cached = getCachedHub(cacheKey);
                if (cached != null) {
                    return cached;
                }
            } catch (DataAccessException e) {
                return fallbackToDbWithBulkhead(hubId, "Redis cache retry read failed", e);
            }
        }

        return fallbackToDbWithBulkhead(hubId, "Single-flight wait exhausted", null);
    }

    private HubRes fallbackToDbWithBulkhead(UUID hubId, String reason, RuntimeException cause) {
        if (!dbFallbackBulkhead.tryAcquire()) {
            log.warn("Hub DB fallback rejected. hubId={}, reason={}", hubId, reason, cause);
            throw new BusinessException(ErrorCode.HUB_CACHE_FALLBACK_UNAVAILABLE);
        }

        try {
            log.warn("Hub DB fallback accepted. hubId={}, reason={}", hubId, reason, cause);
            return hubService.getById(hubId);
        } finally {
            dbFallbackBulkhead.release();
        }
    }

    private HubRes getCachedHub(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached instanceof HubRes hubRes) {
            return hubRes;
        }

        log.warn("Unexpected hub cache value type. key={}, type={}", cacheKey, cached.getClass().getName());
        return null;
    }

    private void cacheHub(String cacheKey, HubRes hub) {
        redisTemplate.opsForValue().set(cacheKey, hub, cacheProperties.getTtl());
    }

    private void releaseLock(String lockKey, String lockValue) {
        try {
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockValue);
        } catch (DataAccessException e) {
            log.warn("Failed to release hub single-flight lock. lockKey={}", lockKey, e);
        }
    }

    private void sleepBeforeRetry(UUID hubId) {
        try {
            Thread.sleep(cacheProperties.getWaitInterval().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for hub cache fill. hubId={}", hubId, e);
            throw new BusinessException(ErrorCode.HUB_CACHE_FALLBACK_UNAVAILABLE, e);
        }
    }

    private String cacheKey(UUID hubId) {
        return CACHE_KEY_PREFIX + hubId;
    }

    private String lockKey(UUID hubId) {
        return LOCK_KEY_PREFIX + hubId;
    }
}
