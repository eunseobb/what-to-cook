package com.whattocook.user.domain;

/** 사용자가 피하는 재료의 사유. ALLERGY만 추천 하드 필터로 쓰고, DISLIKE는 하드 필터에 쓰지 않는다. */
public enum ExclusionType {
    ALLERGY,
    DISLIKE
}
