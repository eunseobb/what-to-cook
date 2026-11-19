package com.whattocook.global.error;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. 도메인 예외는 이 클래스를 상속한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}
