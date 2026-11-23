package com.whattocook.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 온보딩 정보 전체 교체 요청. 비운 목록(또는 생략)은 해당 항목을 모두 지운다.
 *
 * @param tagIds               선호 태그 id 목록 (GET /api/tags)
 * @param allergyIngredientIds 알레르기 재료 id 목록 (재료 자동완성 결과)
 * @param dislikeIngredientIds 비선호 재료 id 목록
 */
public record OnboardingRequest(
        @Size(max = 50) List<@NotNull Long> tagIds,
        @Size(max = 100) List<@NotNull Long> allergyIngredientIds,
        @Size(max = 100) List<@NotNull Long> dislikeIngredientIds) {

    public OnboardingRequest {
        tagIds = tagIds == null ? List.of() : tagIds;
        allergyIngredientIds = allergyIngredientIds == null ? List.of() : allergyIngredientIds;
        dislikeIngredientIds = dislikeIngredientIds == null ? List.of() : dislikeIngredientIds;
    }
}
