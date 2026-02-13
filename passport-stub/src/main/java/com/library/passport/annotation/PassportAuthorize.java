package com.library.passport.annotation;

import com.library.passport.entity.PassportUserRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PassportAuthorize {
    PassportUserRole[] allowedRoles() default {};
}
