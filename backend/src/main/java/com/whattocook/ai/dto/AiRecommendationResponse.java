package com.whattocook.ai.dto;

import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.dto.RecipeDetailResponse;
import java.util.List;
import java.util.UUID;

/**
 * @param jobId   폴링용 작업 id
 * @param status  PENDING이면 GET /api/recommendations/ai/{jobId}로 다시 조회한다.
 * @param recipes COMPLETED일 때만 채운다. source=AI_GENERATED로 "AI 생성" 배지를 표시한다.
 * @param errorCode    FAILED일 때만 채운다. 실패 원인 코드 (예: AI003 서킷 오픈, AI004 타임아웃)
 * @param errorMessage FAILED일 때만 채운다. 사용자에게 보여줄 메시지
 */
public record AiRecommendationResponse(UUID jobId, AiRecipeJobStatus status, List<RecipeDetailResponse> recipes,
                                       String errorCode, String errorMessage) {

    public static AiRecommendationResponse pending(UUID jobId) {
        return new AiRecommendationResponse(jobId, AiRecipeJobStatus.PENDING, null, null, null);
    }

    public static AiRecommendationResponse completed(UUID jobId, List<RecipeDetailResponse> recipes) {
        return new AiRecommendationResponse(jobId, AiRecipeJobStatus.COMPLETED, recipes, null, null);
    }

    public static AiRecommendationResponse failed(UUID jobId, ErrorCode errorCode) {
        return new AiRecommendationResponse(jobId, AiRecipeJobStatus.FAILED, null,
                errorCode.getCode(), errorCode.getMessage());
    }
}
