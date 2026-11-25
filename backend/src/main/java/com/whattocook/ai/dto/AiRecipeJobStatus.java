package com.whattocook.ai.dto;

public enum AiRecipeJobStatus {
    /** 생성 중. 잠시 뒤 다시 조회한다. */
    PENDING,
    COMPLETED,
    /** 생성·검증 실패. 다시 요청할 수 있다. */
    FAILED
}
