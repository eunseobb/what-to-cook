package com.whattocook.ai.controller;

import com.whattocook.ai.service.AiRecipeListService;
import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/recipes/ai는 레시피 목록 공개 GET 규칙(/api/recipes/*)에 걸려 비로그인도 볼 수 있다.
 * 리터럴 경로라 /api/recipes/{recipeId}보다 먼저 매칭된다.
 */
@Tag(name = "AI Recommendation", description = "LLM 기타 추천")
@Validated
@RestController
@RequiredArgsConstructor
public class AiRecipeListController {

    private final AiRecipeListService aiRecipeListService;

    @Operation(summary = "AI 생성 레시피 목록",
            description = "AI 생성 레시피를 최신순 커서 페이지네이션으로 준다. 로그인하면 알레르기 재료가 든 레시피를 뺀다. "
                    + "다음 페이지는 응답의 nextCursor를 cursor로 보낸다.")
    @GetMapping("/api/recipes/ai")
    public CursorPageResponse<RecipeSummaryResponse> list(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "이전 페이지의 nextCursor") @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return aiRecipeListService.list(userId, cursor, size);
    }
}
