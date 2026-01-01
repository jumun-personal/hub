package com.jumunhasyeo.stock.infrastructure.repository;

import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import com.jumunhasyeo.hub.hub.infrastructure.repository.JpaHubRepository;
import com.jumunhasyeo.hub.hub.infrastructure.repository.JpaHubRepositoryCustom;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import com.jumunhasyeo.testsupport.AbstractJpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaStockRepositoryCustomImplTest extends AbstractJpaRepositoryTest {

    @Autowired
    private JpaHubRepository hubRepository;

    @Autowired
    private JpaHubRepositoryCustom hubRepositoryCustom;

    private UUID product1;

    @Override
    protected void beforeEachAfterTruncate() {
        product1 = UUID.randomUUID();

        // 테스트 데이터 생성
        Hub hub1 = createHub("서울 허브", "서울시 강남구", 37.5, 127.0);
        hubRepository.save(hub1);

        Hub hub2 = createHub("부산 허브", "부산시 해운대구", 35.1, 129.1);
        hubRepository.save(hub2);

        Hub hub3 = createHub("대구 허브", "대구시 중구", 35.8, 128.6);
        hubRepository.save(hub3);

        Hub hub4 = createHub("인천 허브", "인천시 연수구", 37.4, 126.7);
        hubRepository.save(hub4);
    }

    @Test
    @DisplayName("이름으로 Hub를 검색할 수 있다.")
    public void search_ByName_success() {
        HubSearchCondition condition = HubSearchCondition.builder()
                .name("서울")
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<HubRes> result = hubRepositoryCustom.searchHubsByCondition(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("서울 허브");
    }

    @Test
    @DisplayName("주소로 Hub를 검색할 수 있다.")
    public void search_ByStreet_success() {
        HubSearchCondition condition = HubSearchCondition.builder()
                .street("강남")
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<HubRes> result = hubRepositoryCustom.searchHubsByCondition(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).address()).contains("강남");
    }


    @Test
    @DisplayName("복합 조건으로 Hub를 검색할 수 있다.")
    public void search_ByMultipleConditions_success() {
        HubSearchCondition condition = HubSearchCondition.builder()
                .name("인천 허브")
                .street("인천시 연수구")
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<HubRes> result = hubRepositoryCustom.searchHubsByCondition(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("인천 허브");
    }

    @Test
    @DisplayName("페이징 처리를 할 수 있다.")
    public void search_WithPaging_success() {
        HubSearchCondition condition = HubSearchCondition.builder().build();
        Pageable pageable = PageRequest.of(0, 2);

        Page<HubRes> result = hubRepositoryCustom.searchHubsByCondition(condition, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("두 번째 페이지를 조회할 수 있다.")
    public void search_SecondPage_success() {
        HubSearchCondition condition = HubSearchCondition.builder().build();
        Pageable pageable = PageRequest.of(1, 2);

        Page<HubRes> result = hubRepositoryCustom.searchHubsByCondition(condition, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("조건 없이 모든 Hub를 조회할 수 있다.")
    public void search_WithEmptyCondition_success() {
        HubSearchCondition condition = HubSearchCondition.builder().build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<HubRes> result = hubRepositoryCustom.searchHubsByCondition(condition, pageable);

        assertThat(result.getContent()).hasSize(4);
    }

    @Test
    @DisplayName("존재하지 않는 조건으로 검색 시 빈 결과를 반환한다.")
    public void search_WithNonExistentCondition__success() {
        HubSearchCondition condition = HubSearchCondition.builder()
                .name("제주")
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<HubRes> result = hubRepositoryCustom.searchHubsByCondition(condition, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("이름과 주소를 동시에 검색할 수 있다.")
    public void search_ByNameAndStreet_success() {
        HubSearchCondition condition = HubSearchCondition.builder()
                .name("서울")
                .street("강남")
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<HubRes> result = hubRepositoryCustom.searchHubsByCondition(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("서울 허브");
    }

    @Test
    @DisplayName("삭제된 허브는 조회되지 않는다")
    public void search_deleted_notfound() {
        Hub hub = createHub("송파허브", "송파대로", 12.6, 15.4);
        hub.markDeleted(1L);
        testEntityManager.persistAndFlush(hub);

        HubSearchCondition condition = HubSearchCondition.builder()
                .name("송파허브")
                .street("송파대로")
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<HubRes> result = hubRepositoryCustom.searchHubsByCondition(condition, pageable);

        assertThat(result.getContent()).hasSize(0);
    }

    private static Hub createHub(String name, String street, Double lat, Double lon) {
        return Hub.builder()
                .name(name)
                .address(Address.of(street, Coordinate.of(lat, lon)))
                .build();
    }
}
