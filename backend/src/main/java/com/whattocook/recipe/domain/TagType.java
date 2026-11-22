package com.whattocook.recipe.domain;

/** 태그 종류. 레시피 태그와 온보딩 선호 태그(F3)가 같은 분류를 쓴다. */
public enum TagType {
    CUISINE,    // 음식 종류
    TASTE,      // 맛
    PURPOSE,    // 목적
    DIFFICULTY  // 난이도
}
