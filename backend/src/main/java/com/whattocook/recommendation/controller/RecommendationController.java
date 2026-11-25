package com.whattocook.recommendation.controller;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recommendation.dto.RecommendationRequest;
import com.whattocook.recommendation.dto.RecommendedRecipeResponse;
import com.whattocook.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recommendation", description = "재료 기반 레시피 추천")
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(summary = "재료 기반 커뮤니티 레시피 추천",
            description = "보유 재료로 만들 수 있는 커뮤니티 레시피를 일치율·취향·인기도 점수순으로 준다. "
                    + "알레르기 재료가 든 레시피는 뺀다. 다음 페이지는 응답의 nextCursor를 cursor로 보낸다.")
    @PostMapping
    public CursorPageResponse<RecommendedRecipeResponse> recommend(@AuthenticationPrincipal Long userId,
                                                                   @Valid @RequestBody RecommendationRequest request) {
        return recommendationService.recommend(userId, request);
    }
}
