package com.whattocook.community.controller;

import com.whattocook.community.service.RecipeBookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recipe Bookmark", description = "레시피 저장(북마크)")
@RestController
@RequestMapping("/api/recipes/{recipeId}/bookmarks")
@RequiredArgsConstructor
public class RecipeBookmarkController {

    private final RecipeBookmarkService recipeBookmarkService;

    @Operation(summary = "레시피 저장", description = "멱등. 이미 저장한 레시피여도 204. 삭제된 레시피는 404(R001).")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bookmark(@AuthenticationPrincipal Long userId, @PathVariable Long recipeId) {
        recipeBookmarkService.bookmark(userId, recipeId);
    }

    @Operation(summary = "레시피 저장 해제", description = "멱등. 저장하지 않은 레시피여도 204.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbookmark(@AuthenticationPrincipal Long userId, @PathVariable Long recipeId) {
        recipeBookmarkService.unbookmark(userId, recipeId);
    }
}
