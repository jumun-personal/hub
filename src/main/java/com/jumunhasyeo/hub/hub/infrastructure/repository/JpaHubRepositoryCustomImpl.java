package com.jumunhasyeo.hub.hub.infrastructure.repository;

import com.jumunhasyeo.hub.hub.application.dto.response.HubRes;
import com.jumunhasyeo.hub.hub.domain.entity.QHub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.presentation.dto.HubSearchCondition;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaHubRepositoryCustomImpl implements JpaHubRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * Hub 조건 조회
     */
    @Override
    public Page<HubRes> searchHubsByCondition(
            HubSearchCondition condition,
            Pageable pageable
    ) {
        QHub hub = QHub.hub;

        // 1. 데이터 조회
        List<HubRes> content = queryFactory
                .select(Projections.constructor(
                        HubRes.class,
                        hub.hubId,
                        hub.name,
                        hub.address.street,
                        hub.address.coordinate.latitude,
                        hub.address.coordinate.longitude
                ))
                .from(hub)
                .where(
                        isNotDeleted(),
                        isActive(),
                        nameContains(condition.getName()),
                        streetContains(condition.getStreet())
                )
                .orderBy(hub.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 2. 전체 개수 조회 (count 쿼리)
        Long total = queryFactory
                .select(hub.count())
                .from(hub)
                .where(
                        isNotDeleted(),
                        isActive(),
                        nameContains(condition.getName()),
                        streetContains(condition.getStreet())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    // ========== BooleanExpression 헬퍼 메서드 ==========

    private BooleanExpression isNotDeleted() {
        QHub hub = QHub.hub;
        return hub.deletedAt.isNull();
    }

    private BooleanExpression isActive() {
        QHub hub = QHub.hub;
        return hub.status.isNull().or(hub.status.eq(HubStatus.ACTIVE));
    }

    private BooleanExpression nameContains(String name) {
        QHub hub = QHub.hub;
        return StringUtils.hasText(name) ? hub.name.containsIgnoreCase(name) : null;
    }

    private BooleanExpression streetContains(String street) {
        QHub hub = QHub.hub;
        return StringUtils.hasText(street) ? hub.address.street.containsIgnoreCase(street) : null;
    }
}
