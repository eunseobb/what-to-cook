package com.whattocook.recipe.domain;

/** 레시피 목록 정렬. */
public enum RecipeSort {
    LATEST,     // 최신순 (id 내림차순)
    POPULAR     // 인기순 (좋아요 수, 작성 시각, id 내림차순)
}
