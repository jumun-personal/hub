package com.jumunhasyeo.common.Idempotency;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 처리 중 상태 TTL (초 단위)
     */
    long processingTtlSeconds() default 300;

    /**
     * 성공 상태 TTL (초 단위)
     */
    long successTtlSeconds() default 86_400;

    /**
     * 멱등키 namespace prefix
     */
    String keyPrefix() default "";
}
