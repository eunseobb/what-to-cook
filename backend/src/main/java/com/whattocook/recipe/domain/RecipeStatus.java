package com.whattocook.recipe.domain;

public enum RecipeStatus {
    PUBLISHED,
    HIDDEN,     // 신고 누적 등으로 숨김
    DELETED     // 작성자 삭제(소프트 삭제)
}
