package com.jumunhasyeo.hub.hub.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QHub is a Querydsl query type for Hub
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QHub extends EntityPathBase<Hub> {

    private static final long serialVersionUID = -1081729269L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QHub hub = new QHub("hub");

    public final com.jumunhasyeo.common.QBaseEntity _super = new com.jumunhasyeo.common.QBaseEntity(this);

    public final com.jumunhasyeo.hub.hub.domain.vo.QAddress address;

    public final SetPath<HubRelation, QHubRelation> branchHubRelations = this.<HubRelation, QHubRelation>createSet("branchHubRelations", HubRelation.class, QHubRelation.class, PathInits.DIRECT2);

    public final SetPath<HubRelation, QHubRelation> centerHubRelations = this.<HubRelation, QHubRelation>createSet("centerHubRelations", HubRelation.class, QHubRelation.class, PathInits.DIRECT2);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    //inherited
    public final NumberPath<Long> createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> deletedAt = _super.deletedAt;

    //inherited
    public final NumberPath<Long> deletedBy = _super.deletedBy;

    public final ComparablePath<java.util.UUID> hubId = createComparable("hubId", java.util.UUID.class);

    public final EnumPath<HubType> hubType = createEnum("hubType", HubType.class);

    //inherited
    public final BooleanPath isDeleted = _super.isDeleted;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifiedAt = _super.modifiedAt;

    //inherited
    public final NumberPath<Long> modifiedBy = _super.modifiedBy;

    public final StringPath name = createString("name");

    public final EnumPath<HubStatus> status = createEnum("status", HubStatus.class);

    public QHub(String variable) {
        this(Hub.class, forVariable(variable), INITS);
    }

    public QHub(Path<? extends Hub> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QHub(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QHub(PathMetadata metadata, PathInits inits) {
        this(Hub.class, metadata, inits);
    }

    public QHub(Class<? extends Hub> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.address = inits.isInitialized("address") ? new com.jumunhasyeo.hub.hub.domain.vo.QAddress(forProperty("address"), inits.get("address")) : null;
    }

}

