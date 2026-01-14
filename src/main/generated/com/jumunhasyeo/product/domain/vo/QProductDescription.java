package com.jumunhasyeo.product.domain.vo;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QProductDescription is a Querydsl query type for ProductDescription
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QProductDescription extends BeanPath<ProductDescription> {

    private static final long serialVersionUID = 574822414L;

    public static final QProductDescription productDescription = new QProductDescription("productDescription");

    public final StringPath description = createString("description");

    public QProductDescription(String variable) {
        super(ProductDescription.class, forVariable(variable));
    }

    public QProductDescription(Path<? extends ProductDescription> path) {
        super(path.getType(), path.getMetadata());
    }

    public QProductDescription(PathMetadata metadata) {
        super(ProductDescription.class, metadata);
    }

}

