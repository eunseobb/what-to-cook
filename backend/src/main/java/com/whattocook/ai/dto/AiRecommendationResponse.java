package com.whattocook.ai.dto;

import com.whattocook.recipe.dto.RecipeDetailResponse;
import java.util.List;
import java.util.UUID;

/**
 * @param jobId   폴링용 작업 id
 * @param status  PENDING이면 GET /api/recommendations/ai/{jobId}로 다시 조회한다.
 * @param recipes COMPLETED일 때만 채운다. source=AI_GENERATED로 "AI 생성" 배지를 표시한다.
 */
public record AiRecommendationResponse(UUID jobId, AiRecipeJobStatus status, List<RecipeDetailResponse> recipes) {
}
