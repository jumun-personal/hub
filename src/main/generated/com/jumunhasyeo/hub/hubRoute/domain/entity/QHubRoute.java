package com.jumunhasyeo.hub.hubRoute.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QHubRoute is a Querydsl query type for HubRoute
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QHubRoute extends EntityPathBase<HubRoute> {

    private static final long serialVersionUID = 1655134175L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QHubRoute hubRoute = new QHubRoute("hubRoute");

    public final com.jumunhasyeo.common.QBaseEntity _super = new com.jumunhasyeo.common.QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    //inherited
    public final NumberPath<Long> createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> deletedAt = _super.deletedAt;

    //inherited
    public final NumberPath<Long> deletedBy = _super.deletedBy;

    public final com.jumunhasyeo.hub.hub.domain.entity.QHub endHub;

    //inherited
    public final BooleanPath isDeleted = _super.isDeleted;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifiedAt = _super.modifiedAt;

    //inherited
    public final NumberPath<Long> modifiedBy = _super.modifiedBy;

    public final ComparablePath<java.util.UUID> routeId = createComparable("routeId", java.util.UUID.class);

    public final com.jumunhasyeo.hub.hubRoute.domain.vo.QRouteWeight routeWeight;

    public final com.jumunhasyeo.hub.hub.domain.entity.QHub startHub;

    public QHubRoute(String variable) {
        this(HubRoute.class, forVariable(variable), INITS);
    }

    public QHubRoute(Path<? extends HubRoute> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QHubRoute(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QHubRoute(PathMetadata metadata, PathInits inits) {
        this(HubRoute.class, metadata, inits);
    }

    public QHubRoute(Class<? extends HubRoute> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.endHub = inits.isInitialized("endHub") ? new com.jumunhasyeo.hub.hub.domain.entity.QHub(forProperty("endHub"), inits.get("endHub")) : null;
        this.routeWeight = inits.isInitialized("routeWeight") ? new com.jumunhasyeo.hub.hubRoute.domain.vo.QRouteWeight(forProperty("routeWeight")) : null;
        this.startHub = inits.isInitialized("startHub") ? new com.jumunhasyeo.hub.hub.domain.entity.QHub(forProperty("startHub"), inits.get("startHub")) : null;
    }

}

