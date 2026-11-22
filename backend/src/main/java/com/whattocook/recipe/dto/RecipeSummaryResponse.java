package com.whattocook.recipe.dto;

import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import java.time.LocalDateTime;

/** 목록 항목. 상세 정보(재료·단계)는 상세 API로 조회한다. */
public record RecipeSummaryResponse(
        Long id,
        String title,
        String mainImageUrl,
        RecipeSource source,
        Difficulty difficulty,
        int cookTimeMinutes,
        int servings,
        int likeCount,
        Long authorId,
        String authorNickname,
        LocalDateTime createdAt) {

    public static RecipeSummaryResponse from(Recipe recipe) {
        return new RecipeSummaryResponse(recipe.getId(), recipe.getTitle(), recipe.getMainImageUrl(),
                recipe.getSource(), recipe.getDifficulty(), recipe.getCookTimeMinutes(), recipe.getServings(),
                recipe.getLikeCount(),
                recipe.getAuthor() == null ? null : recipe.getAuthor().getId(),
                recipe.getAuthor() == null ? null : recipe.getAuthor().getNickname(),
                recipe.getCreatedAt());
    }
}
