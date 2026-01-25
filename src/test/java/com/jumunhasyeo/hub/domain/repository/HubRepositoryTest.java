package com.jumunhasyeo.hub.domain.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.testsupport.AbstractJpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HubRepositoryTest extends AbstractJpaRepositoryTest {

    @Autowired
    private HubRepository hubRepository;
    @Test
    @DisplayName("hub를 저장할 수 있다.")
    void save_hub_success() {
        // given
        Hub saveHub = hubRepository.save(createHub());

        // when
        Optional<Hub> findHub = hubRepository.findById(saveHub.getHubId());

        // then
        assertThat(findHub).isNotNull();
        assertThat(findHub.get().getHubId()).isEqualTo(saveHub.getHubId());
    }

    @Test
    @DisplayName("hub를 id로 조회할 수 있다.")
    public void findById_hub_success() {
        Hub savedHub = createHub();
        testEntityManager.persistAndFlush(savedHub);

        Optional<Hub> findHub = hubRepository.findById(savedHub.getHubId());

        assertThat(findHub).isNotNull();
        assertThat(findHub.get().getHubId()).isEqualTo(savedHub.getHubId());
    }

    @Test
    @DisplayName("hub를 id로 삭제된 데이터는 조회되지 않는다.")
    public void findById_NotDeleted_success() {
        Hub deletedHub = createHub();
        deletedHub.markDeleted(1L);

        testEntityManager.persistAndFlush(deletedHub);

        Optional<Hub> findHub = hubRepository.findById(deletedHub.getHubId());

        assertThat(findHub.isEmpty()).isTrue();
    }

    // ===================== findAllByHubType 테스트 =====================

    @Test
    @DisplayName("HubType으로 허브 목록을 조회할 수 있다.")
    void findAllByHubType_success() {
        //given
        Hub centerHub1 = createHubWithType("서울 중앙", HubType.CENTER);
        Hub centerHub2 = createHubWithType("부산 중앙", HubType.CENTER);
        Hub branchHub = createHubWithType("강남 지점", HubType.BRANCH);
        testEntityManager.persistAndFlush(centerHub1);
        testEntityManager.persistAndFlush(centerHub2);
        testEntityManager.persistAndFlush(branchHub);

        //when
        List<Hub> centerHubs = hubRepository.findAllByHubType(HubType.CENTER);

        //then
        assertThat(centerHubs).hasSize(2);
        assertThat(centerHubs).extracting(Hub::getName)
                .containsExactlyInAnyOrder("서울 중앙", "부산 중앙");
    }

    @Test
    @DisplayName("HubType으로 조회 시 삭제된 허브는 조회되지 않는다.")
    void findAllByHubType_excludesDeleted() {
        //given
        Hub activeCenter = createHubWithType("서울 중앙", HubType.CENTER);
        Hub deletedCenter = createHubWithType("부산 중앙", HubType.CENTER);
        deletedCenter.markDeleted(1L);
        testEntityManager.persistAndFlush(activeCenter);
        testEntityManager.persistAndFlush(deletedCenter);

        //when
        List<Hub> centerHubs = hubRepository.findAllByHubType(HubType.CENTER);

        //then
        assertThat(centerHubs).hasSize(1);
        assertThat(centerHubs.get(0).getName()).isEqualTo("서울 중앙");
    }

    // ===================== findAll 테스트 =====================

    @Test
    @DisplayName("모든 허브를 조회할 수 있다.")
    void findAll_success() {
        //given
        Hub hub1 = createHubWithType("서울 중앙", HubType.CENTER);
        Hub hub2 = createHubWithType("강남 지점", HubType.BRANCH);
        testEntityManager.persistAndFlush(hub1);
        testEntityManager.persistAndFlush(hub2);

        //when
        List<Hub> hubs = hubRepository.findAll();

        //then
        assertThat(hubs).hasSize(2);
    }

    @Test
    @DisplayName("모든 허브 조회 시 삭제된 허브는 조회되지 않는다.")
    void findAll_excludesDeleted() {
        //given
        Hub activeHub = createHubWithType("서울 중앙", HubType.CENTER);
        Hub deletedHub = createHubWithType("부산 중앙", HubType.CENTER);
        deletedHub.markDeleted(1L);
        testEntityManager.persistAndFlush(activeHub);
        testEntityManager.persistAndFlush(deletedHub);

        //when
        List<Hub> hubs = hubRepository.findAll();

        //then
        assertThat(hubs).hasSize(1);
        assertThat(hubs.get(0).getName()).isEqualTo("서울 중앙");
    }

    // ===================== count 테스트 =====================

    @Test
    @DisplayName("허브 개수를 조회할 수 있다.")
    void count_success() {
        //given
        Hub hub1 = createHubWithType("서울 중앙", HubType.CENTER);
        Hub hub2 = createHubWithType("강남 지점", HubType.BRANCH);
        Hub hub3 = createHubWithType("부산 중앙", HubType.CENTER);
        testEntityManager.persistAndFlush(hub1);
        testEntityManager.persistAndFlush(hub2);
        testEntityManager.persistAndFlush(hub3);

        //when
        long count = hubRepository.count();

        //then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("허브 개수 조회 시 삭제된 허브는 제외된다.")
    void count_excludesDeleted() {
        //given
        Hub activeHub = createHubWithType("서울 중앙", HubType.CENTER);
        Hub deletedHub = createHubWithType("부산 중앙", HubType.CENTER);
        deletedHub.markDeleted(1L);
        testEntityManager.persistAndFlush(activeHub);
        testEntityManager.persistAndFlush(deletedHub);

        //when
        long count = hubRepository.count();

        //then
        assertThat(count).isEqualTo(1);
    }

    // ===================== existsById 테스트 =====================

    @Test
    @DisplayName("허브 존재 여부를 확인할 수 있다 - 존재하는 경우")
    void existsById_exists_returnsTrue() {
        //given
        Hub hub = createHubWithType("서울 중앙", HubType.CENTER);
        testEntityManager.persistAndFlush(hub);

        //when
        boolean exists = hubRepository.existById(hub.getHubId());

        //then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("허브 존재 여부를 확인할 수 있다 - 존재하지 않는 경우")
    void existsById_notExists_returnsFalse() {
        //given
        UUID nonExistentId = UUID.randomUUID();

        //when
        boolean exists = hubRepository.existById(nonExistentId);

        //then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("삭제된 허브는 존재하지 않는 것으로 처리된다.")
    void existsById_deleted_returnsFalse() {
        //given
        Hub deletedHub = createHubWithType("서울 중앙", HubType.CENTER);
        deletedHub.markDeleted(1L);
        testEntityManager.persistAndFlush(deletedHub);

        //when
        boolean exists = hubRepository.existById(deletedHub.getHubId());

        //then
        assertThat(exists).isFalse();
    }

    // ===================== Helper 메서드 =====================

    private static Hub createHub() {
        return Hub.builder()
                .name("송파 허브")
                .status(HubStatus.COMPLETE)
                .address(Address.of("street", Coordinate.of(12.6, 12.6)))
                .build();
    }

    private static Hub createHubWithType(String name, HubType hubType) {
        return Hub.builder()
                .name(name)
                .hubType(hubType)
                .status(HubStatus.COMPLETE)
                .address(Address.of("주소", Coordinate.of(37.5, 127.0)))
                .build();
    }
}
