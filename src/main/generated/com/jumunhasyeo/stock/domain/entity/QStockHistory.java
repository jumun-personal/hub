package com.jumunhasyeo.stock.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QStockHistory is a Querydsl query type for StockHistory
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QStockHistory extends EntityPathBase<StockHistory> {

    private static final long serialVersionUID = 696369710L;

    public static final QStockHistory stockHistory = new QStockHistory("stockHistory");

    public final com.jumunhasyeo.common.QBaseEntity _super = new com.jumunhasyeo.common.QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    //inherited
    public final NumberPath<Long> createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> deletedAt = _super.deletedAt;

    //inherited
    public final NumberPath<Long> deletedBy = _super.deletedBy;

    public final ComparablePath<java.util.UUID> hubId = createComparable("hubId", java.util.UUID.class);

    public final ComparablePath<java.util.UUID> id = createComparable("id", java.util.UUID.class);

    public final StringPath idempotencyKey = createString("idempotencyKey");

    //inherited
    public final BooleanPath isDeleted = _super.isDeleted;

    public final StringPath memo = createString("memo");

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifiedAt = _super.modifiedAt;

    //inherited
    public final NumberPath<Long> modifiedBy = _super.modifiedBy;

    public final ComparablePath<java.util.UUID> productId = createComparable("productId", java.util.UUID.class);

    public final NumberPath<Integer> quantity = createNumber("quantity", Integer.class);

    public final EnumPath<StockHistory.StockHistoryType> type = createEnum("type", StockHistory.StockHistoryType.class);

    public QStockHistory(String variable) {
        super(StockHistory.class, forVariable(variable));
    }

    public QStockHistory(Path<? extends StockHistory> path) {
        super(path.getType(), path.getMetadata());
    }

    public QStockHistory(PathMetadata metadata) {
        super(StockHistory.class, metadata);
    }

}

