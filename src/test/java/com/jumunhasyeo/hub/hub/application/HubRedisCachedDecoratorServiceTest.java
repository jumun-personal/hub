package com.jumunhasyeo.hub.hub.application;

import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.application.command.CreateHubCommand;
import com.jumunhasyeo.hub.hub.application.command.DeleteHubCommand;
import com.jumunhasyeo.hub.hub.application.command.UpdateHubCommand;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HubRedisCachedDecoratorServiceTest {

    @Mock
    private HubService hubService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    private HubRedisCachedDecoratorService service;

    private HubCacheProperties cacheProperties;

    @BeforeEach
    void setUp() {
        cacheProperties = new HubCacheProperties();
        cacheProperties.setTtl(Duration.ofMinutes(30));
        cacheProperties.setLockTtl(Duration.ofSeconds(3));
        cacheProperties.setWaitInterval(Duration.ZERO);
        cacheProperties.setMaxWaitAttempts(1);
        cacheProperties.setDbFallbackBulkheadSize(20);

        service = new HubRedisCachedDecoratorService(
                hubService,
                redisTemplate,
                stringRedisTemplate,
                cacheProperties
        );
    }

    @Test
    @DisplayName("create는 내부 hubService에 위임한다.")
    void create_delegates() {
        CreateHubCommand command = CreateHubCommand.createCenter("센터", "주소", 1.0, 2.0, HubType.CENTER);
        HubRes expected = new HubRes(UUID.randomUUID(), "센터", "주소", 1.0, 2.0);
        when(hubService.create(command)).thenReturn(expected);

        HubRes result = service.create(command);

        assertThat(result).isEqualTo(expected);
        verify(hubService).create(command);
    }

    @Test
    @DisplayName("update는 내부 hubService에 위임한다.")
    void update_delegates() {
        UUID hubId = UUID.randomUUID();
        UpdateHubCommand command = new UpdateHubCommand(hubId, "수정", "주소", 3.0, 4.0);
        HubRes expected = new HubRes(hubId, "수정", "주소", 3.0, 4.0);
        when(hubService.update(command)).thenReturn(expected);

        HubRes result = service.update(command);

        assertThat(result).isEqualTo(expected);
        verify(hubService).update(command);
    }

    @Test
    @DisplayName("delete는 내부 hubService에 위임한다.")
    void delete_delegates() {
        DeleteHubCommand command = new DeleteHubCommand(UUID.randomUUID(), 1L);
        when(hubService.delete(command)).thenReturn(command.hubId());

        UUID result = service.delete(command);

        assertThat(result).isEqualTo(command.hubId());
        verify(hubService).delete(command);
    }

    @Test
    @DisplayName("getById는 Redis 캐시 hit이면 DB를 조회하지 않는다.")
    void getById_cacheHit() {
        UUID hubId = UUID.randomUUID();
        String cacheKey = "hub::" + hubId;
        HubRes expected = new HubRes(hubId, "허브", "주소", 11.0, 22.0);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(expected);

        HubRes result = service.getById(hubId);

        assertThat(result).isEqualTo(expected);
        verify(hubService, never()).getById(hubId);
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("getById는 cache miss 시 락 획득 요청만 DB를 조회하고 캐시에 저장한다.")
    void getById_singleFlightLockOwnerLoadsDb() {
        UUID hubId = UUID.randomUUID();
        String cacheKey = "hub::" + hubId;
        String lockKey = "hub:singleflight:lock:" + hubId;
        HubRes expected = new HubRes(hubId, "허브", "주소", 11.0, 22.0);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.setIfAbsent(eq(lockKey), anyString(), eq(cacheProperties.getLockTtl())))
                .thenReturn(true);
        when(hubService.getById(hubId)).thenReturn(expected);

        HubRes result = service.getById(hubId);

        assertThat(result).isEqualTo(expected);
        verify(hubService).getById(hubId);
        verify(valueOperations).set(cacheKey, expected, cacheProperties.getTtl());
        verify(stringRedisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of(lockKey)),
                anyString()
        );
    }

    @Test
    @DisplayName("getById는 락 획득 후 캐시가 채워졌으면 DB를 조회하지 않는다.")
    void getById_doubleCheckPreventsDuplicateLoad() {
        UUID hubId = UUID.randomUUID();
        String cacheKey = "hub::" + hubId;
        String lockKey = "hub:singleflight:lock:" + hubId;
        HubRes expected = new HubRes(hubId, "허브", "주소", 11.0, 22.0);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null, expected);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.setIfAbsent(eq(lockKey), anyString(), eq(cacheProperties.getLockTtl())))
                .thenReturn(true);

        HubRes result = service.getById(hubId);

        assertThat(result).isEqualTo(expected);
        verify(hubService, never()).getById(hubId);
        verify(valueOperations, never()).set(eq(cacheKey), any(), eq(cacheProperties.getTtl()));
    }

    @Test
    @DisplayName("getById는 락을 획득하지 못하면 짧게 대기 후 Redis 캐시를 재조회한다.")
    void getById_waitsForCacheWhenLockNotAcquired() {
        UUID hubId = UUID.randomUUID();
        String cacheKey = "hub::" + hubId;
        String lockKey = "hub:singleflight:lock:" + hubId;
        HubRes expected = new HubRes(hubId, "허브", "주소", 11.0, 22.0);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null, expected);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.setIfAbsent(eq(lockKey), anyString(), eq(cacheProperties.getLockTtl())))
                .thenReturn(false);

        HubRes result = service.getById(hubId);

        assertThat(result).isEqualTo(expected);
        verify(hubService, never()).getById(hubId);
    }

    @Test
    @DisplayName("getById는 Redis 조회 장애 시 bulkhead가 허용하면 DB fallback을 수행한다.")
    void getById_redisFailureFallsBackToDbWithinBulkhead() {
        UUID hubId = UUID.randomUUID();
        String cacheKey = "hub::" + hubId;
        HubRes expected = new HubRes(hubId, "허브", "주소", 11.0, 22.0);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenThrow(new DataAccessResourceFailureException("redis down"));
        when(hubService.getById(hubId)).thenReturn(expected);

        HubRes result = service.getById(hubId);

        assertThat(result).isEqualTo(expected);
        verify(hubService).getById(hubId);
    }

    @Test
    @DisplayName("getById는 Redis 장애 중 DB fallback bulkhead가 꽉 차면 503 성격의 예외를 던진다.")
    void getById_redisFailureRejectsWhenFallbackBulkheadFull() throws Exception {
        cacheProperties.setDbFallbackBulkheadSize(1);
        service = new HubRedisCachedDecoratorService(
                hubService,
                redisTemplate,
                stringRedisTemplate,
                cacheProperties
        );

        UUID hubId = UUID.randomUUID();
        String cacheKey = "hub::" + hubId;
        HubRes expected = new HubRes(hubId, "허브", "주소", 11.0, 22.0);
        CountDownLatch firstFallbackEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstFallback = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenThrow(new DataAccessResourceFailureException("redis down"));
        when(hubService.getById(hubId)).thenAnswer(invocation -> {
            firstFallbackEntered.countDown();
            assertThat(releaseFirstFallback.await(2, TimeUnit.SECONDS)).isTrue();
            return expected;
        });

        try {
            Future<HubRes> first = executorService.submit(() -> service.getById(hubId));
            assertThat(firstFallbackEntered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.getById(hubId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.HUB_CACHE_FALLBACK_UNAVAILABLE);

            releaseFirstFallback.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(expected);
        } finally {
            releaseFirstFallback.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("search는 내부 hubService에 위임한다.")
    void search_delegates() {
        HubSearchCondition condition = HubSearchCondition.builder().name("허브").build();
        Page<HubRes> expected = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(hubService.search(condition, PageRequest.of(0, 10))).thenReturn(expected);

        Page<HubRes> result = service.search(condition, PageRequest.of(0, 10));

        assertThat(result).isEqualTo(expected);
        verify(hubService).search(condition, PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("existById는 내부 hubService에 위임한다.")
    void existById_delegates() {
        UUID hubId = UUID.randomUUID();
        when(hubService.existById(hubId)).thenReturn(Boolean.TRUE);

        Boolean result = service.existById(hubId);

        assertThat(result).isTrue();
        verify(hubService).existById(hubId);
    }

    @Test
    @DisplayName("getAll은 내부 hubService에 위임한다.")
    void getAll_delegates() {
        List<HubRes> expected = List.of(new HubRes(UUID.randomUUID(), "허브", "주소", 1.0, 1.0));
        when(hubService.getAll()).thenReturn(expected);

        List<HubRes> result = service.getAll();

        assertThat(result).isEqualTo(expected);
        verify(hubService).getAll();
    }
}
