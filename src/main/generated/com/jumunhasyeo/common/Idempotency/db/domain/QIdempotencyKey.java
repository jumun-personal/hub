package com.jumunhasyeo.common.Idempotency.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QIdempotencyKey is a Querydsl query type for IdempotencyKey
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QIdempotencyKey extends EntityPathBase<IdempotencyKey> {

    private static final long serialVersionUID = -49506639L;

    public static final QIdempotencyKey idempotencyKey1 = new QIdempotencyKey("idempotencyKey1");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath errorMessage = createString("errorMessage");

    public final DateTimePath<java.time.LocalDateTime> expiresAt = createDateTime("expiresAt", java.time.LocalDateTime.class);

    public final StringPath idempotencyKey = createString("idempotencyKey");

    public final StringPath payload = createString("payload");

    public final EnumPath<IdempotentStatus> status = createEnum("status", IdempotentStatus.class);

    public final EnumPath<IdempotentType> type = createEnum("type", IdempotentType.class);

    public QIdempotencyKey(String variable) {
        super(IdempotencyKey.class, forVariable(variable));
    }

    public QIdempotencyKey(Path<? extends IdempotencyKey> path) {
        super(path.getType(), path.getMetadata());
    }

    public QIdempotencyKey(PathMetadata metadata) {
        super(IdempotencyKey.class, metadata);
    }

}

