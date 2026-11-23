package com.whattocook.community.controller;

import com.whattocook.community.service.RecipeLikeService;
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

@Tag(name = "Recipe Like", description = "레시피 좋아요")
@RestController
@RequestMapping("/api/recipes/{recipeId}/likes")
@RequiredArgsConstructor
public class RecipeLikeController {

    private final RecipeLikeService recipeLikeService;

    @Operation(summary = "좋아요", description = "사용자당 1회. 이미 좋아요했으면 409(L001). 레시피의 likeCount는 주기적으로 반영된다.")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@AuthenticationPrincipal Long userId, @PathVariable Long recipeId) {
        recipeLikeService.like(userId, recipeId);
    }

    @Operation(summary = "좋아요 취소", description = "좋아요하지 않았으면 404(L002).")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@AuthenticationPrincipal Long userId, @PathVariable Long recipeId) {
        recipeLikeService.unlike(userId, recipeId);
    }
}
