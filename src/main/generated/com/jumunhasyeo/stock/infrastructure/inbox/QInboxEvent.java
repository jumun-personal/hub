package com.jumunhasyeo.stock.infrastructure.inbox;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QInboxEvent is a Querydsl query type for InboxEvent
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QInboxEvent extends EntityPathBase<InboxEvent> {

    private static final long serialVersionUID = 1767179544L;

    public static final QInboxEvent inboxEvent = new QInboxEvent("inboxEvent");

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

    public final DateTimePath<java.time.LocalDateTime> processedAt = createDateTime("processedAt", java.time.LocalDateTime.class);

    public final DateTimePath<java.time.LocalDateTime> receivedAt = createDateTime("receivedAt", java.time.LocalDateTime.class);

    public final NumberPath<Integer> retryCount = createNumber("retryCount", Integer.class);

    public final EnumPath<InboxStatus> status = createEnum("status", InboxStatus.class);

    public QInboxEvent(String variable) {
        super(InboxEvent.class, forVariable(variable));
    }

    public QInboxEvent(Path<? extends InboxEvent> path) {
        super(path.getType(), path.getMetadata());
    }

    public QInboxEvent(PathMetadata metadata) {
        super(InboxEvent.class, metadata);
    }

}

