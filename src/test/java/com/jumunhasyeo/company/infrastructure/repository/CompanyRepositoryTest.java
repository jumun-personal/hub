package com.jumunhasyeo.company.infrastructure.repository;

import com.jumunhasyeo.company.domain.entity.Company;
import com.jumunhasyeo.company.domain.entity.CompanyType;
import com.jumunhasyeo.company.domain.repository.CompanyRepository;
import com.jumunhasyeo.testsupport.AbstractJpaRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyRepositoryTest extends AbstractJpaRepositoryTest {

    @Autowired
    private CompanyRepository companyRepository;
    @Test
    @DisplayName("업체를 저장할 수 있다.")
    void save_success() {
        //given
        Company company = createCompany("테스트업체");

        //when
        Company saved = companyRepository.save(company);

        //then
        assertThat(saved.getCompanyId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("테스트업체");
    }

    @Test
    @DisplayName("업체를 ID로 조회할 수 있다.")
    void findById_success() {
        //given
        Company company = createCompany("테스트업체");
        testEntityManager.persistAndFlush(company);

        //when
        Optional<Company> found = companyRepository.findById(company.getCompanyId());

        //then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("테스트업체");
    }

    @Test
    @DisplayName("삭제된 업체는 조회되지 않는다.")
    void findById_excludesDeleted() {
        //given
        Company company = createCompany("테스트업체");
        company.delete(1L);
        testEntityManager.persistAndFlush(company);

        //when
        Optional<Company> found = companyRepository.findById(company.getCompanyId());

        //then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("모든 업체를 조회할 수 있다.")
    void findAll_success() {
        //given
        testEntityManager.persistAndFlush(createCompany("업체1"));
        testEntityManager.persistAndFlush(createCompany("업체2"));

        //when
        List<Company> companies = companyRepository.findAll();

        //then
        assertThat(companies).hasSize(2);
    }

    @Test
    @DisplayName("업체 존재 여부를 확인할 수 있다.")
    void existsById_success() {
        //given
        Company company = createCompany("테스트업체");
        testEntityManager.persistAndFlush(company);

        //when
        boolean exists = companyRepository.existsById(company.getCompanyId());

        //then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("삭제된 업체는 존재하지 않는 것으로 처리된다.")
    void existsById_deleted_returnsFalse() {
        //given
        Company company = createCompany("테스트업체");
        company.delete(1L);
        testEntityManager.persistAndFlush(company);

        //when
        boolean exists = companyRepository.existsById(company.getCompanyId());

        //then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("업체와 허브 소속 여부를 확인할 수 있다 - 소속인 경우")
    void existsByIdAndHubId_true() {
        //given
        UUID hubId = UUID.randomUUID();
        Company company = createCompanyWithHub("테스트업체", hubId);
        testEntityManager.persistAndFlush(company);

        //when
        boolean exists = companyRepository.existsByIdAndHubId(company.getCompanyId(), hubId);

        //then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("업체와 허브 소속 여부를 확인할 수 있다 - 소속이 아닌 경우")
    void existsByIdAndHubId_false() {
        //given
        Company company = createCompany("테스트업체");
        testEntityManager.persistAndFlush(company);

        //when
        boolean exists = companyRepository.existsByIdAndHubId(company.getCompanyId(), UUID.randomUUID());

        //then
        assertThat(exists).isFalse();
    }

    private Company createCompany(String name) {
        return Company.builder()
                .hubId(UUID.randomUUID())
                .name(name)
                .companyType(CompanyType.PRODUCER)
                .address("서울시")
                .build();
    }

    private Company createCompanyWithHub(String name, UUID hubId) {
        return Company.builder()
                .hubId(hubId)
                .name(name)
                .companyType(CompanyType.PRODUCER)
                .address("서울시")
                .build();
    }
}
