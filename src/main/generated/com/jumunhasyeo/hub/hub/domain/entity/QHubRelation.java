package com.jumunhasyeo.hub.hub.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QHubRelation is a Querydsl query type for HubRelation
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QHubRelation extends EntityPathBase<HubRelation> {

    private static final long serialVersionUID = 1689666087L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QHubRelation hubRelation = new QHubRelation("hubRelation");

    public final QHub branchHub;

    public final QHub centerHub;

    public final ComparablePath<java.util.UUID> HubRelationId = createComparable("HubRelationId", java.util.UUID.class);

    public QHubRelation(String variable) {
        this(HubRelation.class, forVariable(variable), INITS);
    }

    public QHubRelation(Path<? extends HubRelation> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QHubRelation(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QHubRelation(PathMetadata metadata, PathInits inits) {
        this(HubRelation.class, metadata, inits);
    }

    public QHubRelation(Class<? extends HubRelation> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.branchHub = inits.isInitialized("branchHub") ? new QHub(forProperty("branchHub"), inits.get("branchHub")) : null;
        this.centerHub = inits.isInitialized("centerHub") ? new QHub(forProperty("centerHub"), inits.get("centerHub")) : null;
    }

}

