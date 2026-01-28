package com.jumunhasyeo.product.infrastructure.repository;

import com.jumunhasyeo.product.application.command.SearchProductCommand;
import com.jumunhasyeo.product.application.dto.ProductRes;
import com.jumunhasyeo.product.domain.entity.Product;
import com.jumunhasyeo.product.domain.vo.CompanyId;
import com.jumunhasyeo.product.domain.vo.Price;
import com.jumunhasyeo.product.domain.vo.ProductDescription;
import com.jumunhasyeo.product.domain.vo.ProductName;
import com.jumunhasyeo.product.presentation.dto.req.ProductSearchCondition;
import com.jumunhasyeo.testsupport.RepositorySliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static com.jumunhasyeo.product.fixtures.ProductFixture.getProduct;
import static org.assertj.core.api.Assertions.assertThat;

public class ProductRepositorySliceTest extends RepositorySliceTest {

    @Autowired
    private JpaProductRepository jpaProductRepository;

    UUID companyId1 = UUID.randomUUID();
    UUID companyId2 = UUID.randomUUID();

    @Override
    protected void beforeEachAfterTruncate() {
        Product product1 = Product.create(CompanyId.of(companyId1), ProductName.of("상품A"), ProductDescription.of("설명A"), Price.of(10000));
        Product product2 = Product.create(CompanyId.of(companyId1), ProductName.of("상품B"), ProductDescription.of("설명B"), Price.of(5000));
        Product product3 = Product.create(CompanyId.of(companyId2), ProductName.of("상품C"), ProductDescription.of("설명C"), Price.of(500));
        Product product4 = Product.create(CompanyId.of(companyId2), ProductName.of("상품D"), ProductDescription.of("설명D"), Price.of(210000));

        jpaProductRepository.saveAll(List.of(product1, product2, product3, product4));

        testEntityManager.flush();
        testEntityManager.clear();
    }

    @Test
    @DisplayName("상품 저장")
    void save_product() {
        // given
        Product product = getProduct();

        // when
        Product response = jpaProductRepository.save(product);

        // then
        assertThat(response.getName()).isEqualTo(product.getName());
        assertThat(response.getPrice()).isEqualTo(product.getPrice());
        assertThat(response.getDescription()).isEqualTo(product.getDescription());
    }

    @Test
    @DisplayName("상품 다건 조회 - 페이징")
    void findAll_Paging() {
        // given
        SearchProductCommand condition = new SearchProductCommand(
                new ProductSearchCondition(null, "상품", 1000000, 0),
                PageRequest.of(0, 2));

        // when
        Page<ProductRes> response = jpaProductRepository.searchProduct(condition);

        // then
        assertThat(response.getContent().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("상품 다건 조회 - 검색 조건 추가")
    void findAll_Condition() {
        // given
        SearchProductCommand condition = new SearchProductCommand(
                new ProductSearchCondition(companyId1, "상품", 1000000, 0),
                PageRequest.of(0, 10));

        // when
        Page<ProductRes> response = jpaProductRepository.searchProduct(condition);

        // then
        assertThat(response.getContent().size()).isEqualTo(2);
    }
}
