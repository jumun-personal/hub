package com.jumunhasyeo.common.exception;


import com.jumunhasyeo.common.ApiRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

import static com.jumunhasyeo.common.exception.ErrorCode.INVALID_JSON;
import static com.jumunhasyeo.common.exception.ErrorCode.VALIDATION_FAILED;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiRes<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex
    ) {
        String message = ex.toString();
        log.warn("Data integrity constraint violated. cause={}", ex.getMostSpecificCause().getMessage());

        if (message != null && (message.contains("uk_hub_name_deleted") || message.contains("p_hub_name_key"))) {
            // 허브 이름 중복
            return ResponseEntity
                    .status(ErrorCode.ALREADY_EXISTS.getStatus())
                    .body(ApiRes.error(ErrorCode.ALREADY_EXISTS.name(), "이미 존재하는 허브 이름입니다."));
        }

        // 기타 데이터 무결성 위반
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiRes.error(ErrorCode.INVALID_INPUT.name(), "데이터 무결성 제약조건 위반입니다."));
    }

    /**
     * JSON 파싱 에러 처리
     * 예시: {"name": "John",} (마지막 콤마로 인한 JSON 문법 오류)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiRes<?>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Failed to parse request JSON. cause={}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiRes.error(INVALID_JSON.name(), INVALID_JSON.getMessage()));
    }

    /**
     * @Valid 유효성 검사 실패 처리
     * 예시: @Email String email -> "invalid" (이메일 형식 불일치)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiRes<?>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Request validation failed. errors={}", errors);

        return ResponseEntity
                .badRequest()
                .body(ApiRes.error(VALIDATION_FAILED.name(), VALIDATION_FAILED.getMessage() + " " + errors));
    }

    /**
     * 비즈니스 로직 예외 처리
     * 예시: throw new BusinessException(ErrorCode.USER_NOT_FOUND)
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiRes<?>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("Business exception. code={}, message={}", errorCode.getCode(), ex.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiRes.error(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * 예상하지 못한 모든 예외 처리
     * 예시: NullPointerException, DB 연결 오류 등
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiRes<?>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiRes.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
