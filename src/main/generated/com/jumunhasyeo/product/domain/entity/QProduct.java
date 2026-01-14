package com.jumunhasyeo.product.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QProduct is a Querydsl query type for Product
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProduct extends EntityPathBase<Product> {

    private static final long serialVersionUID = -540446664L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QProduct product = new QProduct("product");

    public final com.jumunhasyeo.common.QBaseEntity _super = new com.jumunhasyeo.common.QBaseEntity(this);

    public final com.jumunhasyeo.product.domain.vo.QCompanyId companyId;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    //inherited
    public final NumberPath<Long> createdBy = _super.createdBy;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> deletedAt = _super.deletedAt;

    //inherited
    public final NumberPath<Long> deletedBy = _super.deletedBy;

    public final com.jumunhasyeo.product.domain.vo.QProductDescription description;

    public final ComparablePath<java.util.UUID> id = createComparable("id", java.util.UUID.class);

    //inherited
    public final BooleanPath isDeleted = _super.isDeleted;

    //inherited
    public final DateTimePath<java.time.LocalDateTime> modifiedAt = _super.modifiedAt;

    //inherited
    public final NumberPath<Long> modifiedBy = _super.modifiedBy;

    public final com.jumunhasyeo.product.domain.vo.QProductName name;

    public final com.jumunhasyeo.product.domain.vo.QPrice price;

    public QProduct(String variable) {
        this(Product.class, forVariable(variable), INITS);
    }

    public QProduct(Path<? extends Product> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QProduct(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QProduct(PathMetadata metadata, PathInits inits) {
        this(Product.class, metadata, inits);
    }

    public QProduct(Class<? extends Product> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.companyId = inits.isInitialized("companyId") ? new com.jumunhasyeo.product.domain.vo.QCompanyId(forProperty("companyId")) : null;
        this.description = inits.isInitialized("description") ? new com.jumunhasyeo.product.domain.vo.QProductDescription(forProperty("description")) : null;
        this.name = inits.isInitialized("name") ? new com.jumunhasyeo.product.domain.vo.QProductName(forProperty("name")) : null;
        this.price = inits.isInitialized("price") ? new com.jumunhasyeo.product.domain.vo.QPrice(forProperty("price")) : null;
    }

}

