package com.jumunhasyeo.common.Idempotency;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DbIdempotent {

    /**
     * TTL (일 단위)
     */
    int ttlDays() default 7;

    /**
     * 멱등키 namespace prefix
     */
    String keyPrefix() default "";
}
