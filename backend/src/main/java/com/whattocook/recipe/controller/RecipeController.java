package com.whattocook.recipe.controller;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recipe.domain.RecipeSort;
import com.whattocook.recipe.dto.RecipeCreateRequest;
import com.whattocook.recipe.dto.RecipeCreateResponse;
import com.whattocook.recipe.dto.RecipeDetailResponse;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import com.whattocook.recipe.dto.RecipeUpdateRequest;
import com.whattocook.recipe.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recipe", description = "커뮤니티 레시피")
@Validated
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @Operation(summary = "레시피 목록", description = "최신순 커서 페이지네이션. 다음 페이지는 응답의 nextCursor를 cursor로 보낸다.")
    @SecurityRequirements
    @GetMapping
    public CursorPageResponse<RecipeSummaryResponse> list(
            @Parameter(description = "이전 페이지의 nextCursor") @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return recipeService.list(List.of(), RecipeSort.LATEST, null, cursor == null ? null : cursor.toString(), size);
    }

    @Operation(summary = "레시피 상세")
    @SecurityRequirements
    @GetMapping("/{recipeId}")
    public RecipeDetailResponse get(@PathVariable Long recipeId) {
        return recipeService.get(recipeId);
    }

    @Operation(summary = "레시피 등록", description = "이미지는 업로드가 끝난 URL만 받는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeCreateResponse create(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody RecipeCreateRequest request) {
        return new RecipeCreateResponse(recipeService.create(userId, request));
    }

    @Operation(summary = "AI 레시피를 내 레시피로 가져오기",
            description = "AI 생성 레시피를 내 커뮤니티 레시피로 복사한다. 편집은 응답 id로 레시피 수정 API를 쓴다.")
    @PostMapping("/{recipeId}/fork")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeCreateResponse fork(@AuthenticationPrincipal Long userId, @PathVariable Long recipeId) {
        return new RecipeCreateResponse(recipeService.forkAiRecipe(userId, recipeId));
    }

    @Operation(summary = "레시피 수정",description = "작성자만 가능. 보낸 필드만 바꾸고, 재료·단계·태그 목록은 보내면 통째로 교체한다.")
    @PatchMapping("/{recipeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@AuthenticationPrincipal Long userId, @PathVariable Long recipeId,
                       @Valid @RequestBody RecipeUpdateRequest request) {
        recipeService.update(userId, recipeId, request);
    }

    @Operation(summary = "레시피 삭제", description = "작성자만 가능.")
    @DeleteMapping("/{recipeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long recipeId) {
        recipeService.delete(userId, recipeId);
    }
}
