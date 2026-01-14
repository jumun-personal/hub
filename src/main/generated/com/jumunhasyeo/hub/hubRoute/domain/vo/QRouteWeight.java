package com.jumunhasyeo.hub.hubRoute.domain.vo;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QRouteWeight is a Querydsl query type for RouteWeight
 */
@Generated("com.querydsl.codegen.DefaultEmbeddableSerializer")
public class QRouteWeight extends BeanPath<RouteWeight> {

    private static final long serialVersionUID = -1560660340L;

    public static final QRouteWeight routeWeight = new QRouteWeight("routeWeight");

    public final NumberPath<java.math.BigDecimal> distanceKm = createNumber("distanceKm", java.math.BigDecimal.class);

    public final NumberPath<Integer> durationMinutes = createNumber("durationMinutes", Integer.class);

    public QRouteWeight(String variable) {
        super(RouteWeight.class, forVariable(variable));
    }

    public QRouteWeight(Path<? extends RouteWeight> path) {
        super(path.getType(), path.getMetadata());
    }

    public QRouteWeight(PathMetadata metadata) {
        super(RouteWeight.class, metadata);
    }

}

