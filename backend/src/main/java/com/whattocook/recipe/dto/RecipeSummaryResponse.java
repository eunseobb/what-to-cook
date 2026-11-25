package com.whattocook.recipe.dto;

import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.Tag;
import java.time.LocalDateTime;

/**
 * 목록 항목. 상세 정보(재료·단계)는 상세 API로 조회한다.
 * representativeTag는 카드에 보일 태그 1개와 이미지 없을 때의 플레이스홀더 색 키다. 태그가 없으면 null.
 */
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
        TagResponse representativeTag,
        LocalDateTime createdAt) {

    /** 태그는 default_batch_fetch_size로 목록 단위 IN 조회되므로 레시피마다 추가 쿼리가 나가지 않는다. */
    public static RecipeSummaryResponse from(Recipe recipe) {
        Tag tag = recipe.getRepresentativeTag();
        return new RecipeSummaryResponse(recipe.getId(), recipe.getTitle(), recipe.getMainImageUrl(),
                recipe.getSource(), recipe.getDifficulty(), recipe.getCookTimeMinutes(), recipe.getServings(),
                recipe.getLikeCount(),
                recipe.getAuthor() == null ? null : recipe.getAuthor().getId(),
                recipe.getAuthor() == null ? null : recipe.getAuthor().getNickname(),
                tag == null ? null : TagResponse.from(tag),
                recipe.getCreatedAt());
    }
}
