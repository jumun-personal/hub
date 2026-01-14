package com.jumunhasyeo.common.Idempotency.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QDbIdempotentKey is a Querydsl query type for DbIdempotentKey
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QDbIdempotentKey extends EntityPathBase<DbIdempotentKey> {

    private static final long serialVersionUID = 1938282629L;

    public static final QDbIdempotentKey dbIdempotentKey = new QDbIdempotentKey("dbIdempotentKey");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath errorMessage = createString("errorMessage");

    public final DateTimePath<java.time.LocalDateTime> expiresAt = createDateTime("expiresAt", java.time.LocalDateTime.class);

    public final StringPath idempotencyKey = createString("idempotencyKey");

    public final StringPath payload = createString("payload");

    public final EnumPath<IdempotentStatus> status = createEnum("status", IdempotentStatus.class);

    public final EnumPath<IdempotentType> type = createEnum("type", IdempotentType.class);

    public QDbIdempotentKey(String variable) {
        super(DbIdempotentKey.class, forVariable(variable));
    }

    public QDbIdempotentKey(Path<? extends DbIdempotentKey> path) {
        super(path.getType(), path.getMetadata());
    }

    public QDbIdempotentKey(PathMetadata metadata) {
        super(DbIdempotentKey.class, metadata);
    }

}

