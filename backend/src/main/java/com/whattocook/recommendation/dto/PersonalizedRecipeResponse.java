package com.whattocook.recommendation.dto;

import com.whattocook.recipe.dto.RecipeSummaryResponse;

/**
 * @param score 정렬 점수 (취향 점수 × w2 + 인기도 × w3)
 */
public record PersonalizedRecipeResponse(RecipeSummaryResponse recipe, double score) {
}
