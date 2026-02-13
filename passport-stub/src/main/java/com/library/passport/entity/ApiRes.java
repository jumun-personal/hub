package com.library.passport.entity;

public record ApiRes<T>(
        String code,
        String message,
        T data
) {
    public static <T> ApiRes<T> success(T data) {
        return new ApiRes<>(null, null, data);
    }
}
