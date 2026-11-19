package com.whattocook.global.error;

import java.util.List;
import org.springframework.validation.BindingResult;

/**
 * 모든 에러 응답의 공통 포맷.
 *
 * @param code    ErrorCode의 코드 (예: C001)
 * @param message 사용자에게 보여줄 메시지
 * @param errors  필드 검증 오류 목록 (없으면 빈 목록)
 */
public record ErrorResponse(String code, String message, List<FieldError> errors) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors);
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        return of(errorCode, FieldError.from(bindingResult));
    }

    public record FieldError(String field, String value, String reason) {

        public static FieldError of(String field, Object value, String reason) {
            return new FieldError(field, value == null ? "" : String.valueOf(value), reason);
        }

        static List<FieldError> from(BindingResult bindingResult) {
            return bindingResult.getFieldErrors().stream()
                    .map(e -> of(e.getField(), e.getRejectedValue(), e.getDefaultMessage()))
                    .toList();
        }
    }
}
