package com.jumunhasyeo.hub.infrastructure.outbox;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QOutboxEvent is a Querydsl query type for OutboxEvent
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOutboxEvent extends EntityPathBase<OutboxEvent> {

    private static final long serialVersionUID = -498073017L;

    public static final QOutboxEvent outboxEvent = new QOutboxEvent("outboxEvent");

    public final com.jumunhasyeo.common.QBaseEntity _super = new com.jumunhasyeo.common.QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    //inherited
    public final NumberPath<Long> createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> deletedAt = _super.deletedAt;

    //inherited
    public final NumberPath<Long> deletedBy = _super.deletedBy;

    public final StringPath errorMessage = createString("errorMessage");

    public final StringPath eventKey = createString("eventKey");

    public final StringPath eventName = createString("eventName");

    public final ComparablePath<java.util.UUID> id = createComparable("id", java.util.UUID.class);

    //inherited
    public final BooleanPath isDeleted = _super.isDeleted;

    public final NumberPath<Integer> maxRetries = createNumber("maxRetries", Integer.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifiedAt = _super.modifiedAt;

    //inherited
    public final NumberPath<Long> modifiedBy = _super.modifiedBy;

    public final StringPath payload = createString("payload");

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final EnumPath<OutboxStatus> status = createEnum("status", OutboxStatus.class);

    public final StringPath topic = createString("topic");

    public QOutboxEvent(String variable) {
        super(OutboxEvent.class, forVariable(variable));
    }

    public QOutboxEvent(Path<? extends OutboxEvent> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOutboxEvent(PathMetadata metadata) {
        super(OutboxEvent.class, metadata);
    }

}

