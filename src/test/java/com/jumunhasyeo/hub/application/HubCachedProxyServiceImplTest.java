package com.jumunhasyeo.hub.application;

import com.jumunhasyeo.CleanUp;
import com.jumunhasyeo.CommonTestContainer;
import com.jumunhasyeo.InternalIntegrationTestConfig;
import com.jumunhasyeo.hub.hub.application.HubService;
import com.jumunhasyeo.hub.hub.application.command.DeleteHubCommand;
import com.jumunhasyeo.hub.hub.application.command.UpdateHubCommand;
import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Import(InternalIntegrationTestConfig.class)
class HubRedisCachedDecoratorServiceIntegrationTest extends CommonTestContainer {

    @Autowired
    private HubService hubService;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CleanUp cleanUp;

    @BeforeEach
    void setUp() {
        cleanUp.truncateAll();
        clearAllCaches();
    }

    @AfterEach
    void tearDown() {
        clearAllCaches();
    }

    @Test
    @DisplayName("허브 첫 조회 시 Cache Miss 후 DB 조회")
    void getById_FirstCall_CacheMiss() {
        // given
        Hub hub = createHubAndSave();
        UUID hubId = hub.getHubId();

        // when
        HubRes result = hubService.getById(hubId);

        // then
        assertThat(result.id()).isEqualTo(hubId);
        assertThat(result.name()).isEqualTo("송파허브");
        //캐시 조회
        HubRes hubRes = (HubRes) cacheManager.getCache("hub").get(hubId).get();
        assertThat(hubRes).isNotNull();
    }

    @Test
    @DisplayName("허브 두 번째 조회 시 Cache Hit으로 DB 조회 안함")
    void getById_SecondCall_CacheHit() {
        // given
        Hub hub = createHubAndSave();
        UUID hubId = hub.getHubId();
        // 첫 번째 호출 (Cache Miss - DB 조회)
        HubRes firstCall = hubService.getById(hubId);
        // DB에서 데이터 삭제 (캐시만 남음)
        entityManager.remove(hub);
        entityManager.flush();

        // when - 두 번째 호출 (Cache Hit - DB 조회 없이 캐시에서 가져옴)
        HubRes secondCall = hubService.getById(hubId);

        // then
        assertThat(secondCall.id()).isEqualTo(firstCall.id());
        assertThat(secondCall.name()).isEqualTo(firstCall.name());

        // DB에는 없지만 캐시에서 가져온 것 확인
        assertThat(hubRepository.findById(hubId)).isEmpty();
    }

    @Test
    @DisplayName("허브 수정 시 이전 캐시가 새 값으로 교체됨")
    void update_ShouldReplaceOldCache() {
        // given
        Hub hub = createHubAndSave();
        UUID hubId = hub.getHubId();
        // 첫 조회로 캐시 생성
        HubRes beforeUpdate = hubService.getById(hubId);

        // when - 수정
        UpdateHubCommand command = new UpdateHubCommand(
                hubId,
                "강남허브",
                "서울시 강남구",
                37.5,
                127.1
        );
        hubService.update(command);
        //캐시에서 바로 새 값 조회
        HubRes afterUpdate = hubService.getById(hubId);

        // then
        assertThat(afterUpdate.name()).isEqualTo("강남허브");
        assertThat(afterUpdate.address()).isEqualTo("서울시 강남구");
    }

    @Test
    @DisplayName("허브 삭제 시 캐시 제거 된다.")
    void delete_ShouldEvictCache() {
        // given
        Hub hub = createHubAndSave();
        UUID hubId = hub.getHubId();
        // 첫 조회로 캐시 생성
        hubService.getById(hubId);

        // when - 허브 삭제
        DeleteHubCommand command = new DeleteHubCommand(hubId, 1L);
        hubService.delete(command);

        // then - 캐시가 삭제되었는지 확인
        String cacheKey = "hub::" + hubId;
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();
    }

    @Test
    @DisplayName("서로 다른 허브는 독립적으로 캐싱된다.")
    void differentHubs_ShouldCacheSeparately() {
        // given
        Hub hub1 = createHubAndSave("허브1");
        Hub hub2 = createHubAndSave("허브2");

        // when
        HubRes result1 = hubService.getById(hub1.getHubId());
        HubRes result2 = hubService.getById(hub2.getHubId());

        // then
        assertThat(result1.name()).isEqualTo("허브1");
        assertThat(result2.name()).isEqualTo("허브2");

        // 각각 독립적으로 캐싱되었는지 확인
        HubRes hubRes1 = (HubRes) cacheManager.getCache("hub").get(hub1.getHubId()).get();
        HubRes hubRes2 = (HubRes) cacheManager.getCache("hub").get(hub2.getHubId()).get();
        assertThat(hubRes1).isNotNull();
        assertThat(hubRes2).isNotNull();
    }

    private Hub createHubAndSave() {
        return createHubAndSave("송파허브");
    }

    private Hub createHubAndSave(String name) {
        Hub hub = Hub.createBranchHub(
                name,
                Address.of("서울시 송파구", Coordinate.of(37.5, 127.0))
        );
        return hubRepository.save(hub);
    }

    private void clearAllCaches() {
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            });
        }

        // Redis 전체 초기화 (테스트 격리)
        Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()
                .serverCommands()
                .flushAll();
    }
}
