package com.jumunhasyeo.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 공통
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E001", "서버 에러가 발생했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "E002", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "E003", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "E004", "접근이 거부되었습니다."),
    CREATE_VALIDATE_EXCEPTION(HttpStatus.BAD_REQUEST, "E005", "객체 생성에 실패했습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "E006", "입력값 검증에 실패했습니다."),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "E007", "잘못된 JSON 형식입니다."),
    MUST_NOT_NULL(HttpStatus.BAD_REQUEST, "E008", "는(은) null일 수 없습니다."),
    NOT_FOUND_EXCEPTION(HttpStatus.BAD_REQUEST, "EN001", "조회에 실패했습니다."),
    ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "E009", "이미 존재합니다."),
    PROCESSING_CONFLICT_EXCEPTION(HttpStatus.CONFLICT, "C001", "요청이 진행중입니다."),
    //Stock
    STOCK_VALID(HttpStatus.BAD_REQUEST, "ES001", "Stock 유효성 검사에 실패했습니다."),
    STOCK_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "ES002", "재고가 부족합니다."),
    STOCK_MAX_EXCEEDED(HttpStatus.BAD_REQUEST, "ES003", "재고가 최대치를 초과했습니다."),

    //product
    PRODUCT_VALID_FAIL(HttpStatus.BAD_REQUEST, "EP001", "Product 유효성 검사에 실패했습니다"),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "EP002", "존재하지 않는 업체입니다."),
    USER_MUST_NOT_NULL(HttpStatus.BAD_REQUEST, "EP003", "사용자 ID는 필수 입력값 입니다"),
    NAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "EP004", "중복된 상품명 입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "EP005", "존재하지 않는 상품입니다"),

    //Hub
    SUCCESS_CONFLICT_EXCEPTION(HttpStatus.CONFLICT, "C002", "요청이 이미 완료되었습니다."),
    HUB_NOT_FOUND(HttpStatus.BAD_REQUEST, "EH001", "허브를 찾을 수 없습니다."),
    INVALID_HUB_TYPE(HttpStatus.INTERNAL_SERVER_ERROR, "EH002", "유효하지 않은 허브 타입입니다."),
    HUB_CANNOT_BE_NULL(HttpStatus.BAD_REQUEST, "EH003", "허브는 null일 수 없습니다."),
    HUB_MUST_BE_CENTER_TYPE(HttpStatus.BAD_REQUEST, "EH004", "센터 타입 허브여야 합니다."),
    HUB_MUST_BE_BRANCH_TYPE(HttpStatus.BAD_REQUEST, "EH005", "지점 타입 허브여야 합니다."),
    CANNOT_DELETE_CENTER_HUB_WITH_BRANCHES(HttpStatus.BAD_REQUEST, "EH006", "지점 허브가 존재하는 센터 허브는 삭제할 수 없습니다."),
    HUB_CACHE_FALLBACK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EH007", "허브 조회가 일시적으로 제한됩니다."),

    //HubRelation
    HUB_RELATION_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "EHR001", "허브 관계가 이미 존재합니다."),
    CENTER_HUB_ONLY_CAN_ADD_BRANCH_HUB(HttpStatus.BAD_REQUEST, "EHR002", "센터 허브만 지점 허브를 추가할 수 있습니다."),
    BRANCH_HUB_ONLY_CAN_ADD_CENTER_HUB(HttpStatus.BAD_REQUEST, "EHR003", "지점 허브만 센터 허브를 추가할 수 있습니다."),
    BRANCH_NOT_CONNECTED_TO_CENTER(HttpStatus.BAD_REQUEST, "EHR004", "지점 허브가 센터 허브에 연결되지 않았습니다."),

    //Hub Route
    DUPLICATE_HUB_ROUTE(HttpStatus.BAD_REQUEST, "EHRT001", "허브 경로가 이미 존재합니다."),
    CANNOT_CREATE_ROUTE_SAME_HUB(HttpStatus.BAD_REQUEST, "EHRT002", "같은 허브끼리는 경로를 생성할 수 없습니다."),
    CANNOT_CREATE_ROUTE_FROM_DELETED_HUB(HttpStatus.BAD_REQUEST, "EHRT003", "삭제된 허브에서 경로를 생성할 수 없습니다."),
    CANNOT_CREATE_ROUTE_TO_DELETED_HUB(HttpStatus.BAD_REQUEST, "EHRT004", "삭제된 허브로 경로를 생성할 수 없습니다."),
    HUB_ROUTE_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EHRT005", "지도 API 장애로 허브 생성이 일시적으로 제한됩니다."),
    HUB_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "EHRT006", "허브 경로를 찾을 수 없습니다."),

    //API
    MAP_API_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "EAM001", "지도 API 호출에 실패했습니다."),

    //Controller
    CONTROLLER_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "ECI001", "지점 허브 생성 시 센터 허브 ID는 필수입니다."),

    //outbox
    OUTBOX_EVENT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "EO001", "Outbox 이벤트를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
