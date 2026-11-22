package com.whattocook.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드. 도메인별 코드는 접두어로 구분한다 (C: 공통, A: 인증, ...).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C002", "입력값의 타입이 올바르지 않습니다."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "C003", "필수 파라미터가 없습니다."),
    MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, "C004", "요청 본문을 읽을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C005", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C006", "지원하지 않는 HTTP 메서드입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "C007", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 내부 오류가 발생했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A003", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "유효하지 않은 Refresh 토큰입니다. 다시 로그인해 주세요."),

    // User
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U001", "이미 가입된 이메일입니다."),

    // Recipe
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "레시피를 찾을 수 없습니다."),
    NOT_RECIPE_AUTHOR(HttpStatus.FORBIDDEN, "R002", "레시피 작성자만 수정·삭제할 수 있습니다."),
    RECIPE_INGREDIENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "R003", "존재하지 않는 재료가 포함되어 있습니다."),
    DUPLICATE_RECIPE_INGREDIENT(HttpStatus.BAD_REQUEST, "R004", "같은 재료를 두 번 넣을 수 없습니다."),
    RECIPE_TAG_NOT_FOUND(HttpStatus.BAD_REQUEST, "R005", "존재하지 않는 태그가 포함되어 있습니다."),

    // Upload
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "UP001", "JPEG, PNG, WebP 이미지만 업로드할 수 있습니다."),
    IMAGE_EXTENSION_MISMATCH(HttpStatus.BAD_REQUEST, "UP002", "파일 확장자가 이미지 형식과 맞지 않습니다."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "UP003", "이미지 파일이 너무 큽니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
