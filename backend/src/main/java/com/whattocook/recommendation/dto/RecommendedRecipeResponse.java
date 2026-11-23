package com.whattocook.recommendation.dto;

import com.whattocook.recipe.dto.RecipeSummaryResponse;

/**
 * @param matchedIngredientCount  보유한 필요 재료 수 (기본 양념 제외)
 * @param requiredIngredientCount 필요 재료 수 (기본 양념 제외)
 * @param matchRate               재료 일치율 (0~1)
 * @param score                   정렬 점수
 */
public record RecommendedRecipeResponse(
        RecipeSummaryResponse recipe,
        int matchedIngredientCount,
        int requiredIngredientCount,
        double matchRate,
        double score) {
}
