package com.whattocook.recommendation.controller;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recommendation.dto.RecommendationQuery;
import com.whattocook.recommendation.dto.RecommendationRequest;
import com.whattocook.recommendation.dto.RecommendedRecipeResponse;
import com.whattocook.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Operation(summary = "재료 ID·태그 조건 레시피 추천",
            description = "검색 화면 URL과 같은 쿼리스트링(?ingredient=1&ingredient=5&tag=3&tag=7)으로 추천한다. "
                    + "재료는 자동완성 결과의 표준 재료 ID, 태그는 태그 ID이며 태그는 모두 가진 레시피만 남긴다. "
                    + "점수·필터는 POST 추천과 같다.")
    @GetMapping
    public CursorPageResponse<RecommendedRecipeResponse> recommendByQuery(@AuthenticationPrincipal Long userId,
                                                                          @ParameterObject @Valid RecommendationQuery query) {
        return recommendationService.recommend(userId, query);
    }
}
