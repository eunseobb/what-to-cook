package com.whattocook.recommendation.controller;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recommendation.dto.PersonalizedRecipeResponse;
import com.whattocook.recommendation.service.PersonalizedRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recommendation", description = "재료 기반 레시피 추천")
@Validated
@RestController
@RequestMapping("/api/recommendations/personalized")
@RequiredArgsConstructor
public class PersonalizedRecommendationController {

    private final PersonalizedRecommendationService personalizedRecommendationService;

    @Operation(summary = "내 취향 추천 목록",
            description = "재료 입력 없이 선호 태그 취향 점수와 인기도로 정렬한 레시피를 준다. 알레르기 재료가 든 레시피는 뺀다. "
                    + "다음 페이지는 응답의 nextCursor를 cursor로 보낸다.")
    @GetMapping
    public CursorPageResponse<PersonalizedRecipeResponse> recommend(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "이전 페이지의 nextCursor") @RequestParam(required = false) @Pattern(regexp = "\\d{1,4}") String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return personalizedRecommendationService.recommend(userId, cursor, size);
    }
}
