package com.whattocook.user.controller;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import com.whattocook.user.service.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My Page", description = "마이페이지")
@Validated
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @Operation(summary = "내 레시피", description = "작성한 게시 레시피 최신순. 다음 페이지는 nextCursor를 cursor로 보낸다.")
    @GetMapping("/recipes")
    public CursorPageResponse<RecipeSummaryResponse> myRecipes(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "이전 페이지의 nextCursor") @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return myPageService.myRecipes(userId, cursor, size);
    }

    @Operation(summary = "저장한 레시피", description = "저장한 순서 최신순. 숨김·삭제된 레시피는 빠진다.")
    @GetMapping("/bookmarks")
    public CursorPageResponse<RecipeSummaryResponse> bookmarks(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "이전 페이지의 nextCursor") @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return myPageService.bookmarks(userId, cursor, size);
    }
}
