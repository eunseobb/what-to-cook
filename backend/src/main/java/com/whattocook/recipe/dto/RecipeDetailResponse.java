package com.whattocook.recipe.dto;

import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStep;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public record RecipeDetailResponse(
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
        List<IngredientItem> ingredients,
        List<StepItem> steps,
        List<TagItem> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RecipeDetailResponse from(Recipe recipe) {
        List<RecipeStep> steps = recipe.getSteps();
        return new RecipeDetailResponse(recipe.getId(), recipe.getTitle(), recipe.getMainImageUrl(),
                recipe.getSource(), recipe.getDifficulty(), recipe.getCookTimeMinutes(), recipe.getServings(),
                recipe.getLikeCount(),
                recipe.getAuthor() == null ? null : recipe.getAuthor().getId(),
                recipe.getAuthor() == null ? null : recipe.getAuthor().getNickname(),
                recipe.getIngredients().stream().map(IngredientItem::from).toList(),
                IntStream.range(0, steps.size()).mapToObj(i -> StepItem.of(i + 1, steps.get(i))).toList(),
                recipe.getTags().stream().map(TagItem::from)
                        .sorted(Comparator.comparing(TagItem::type).thenComparing(TagItem::id)).toList(),
                recipe.getCreatedAt(), recipe.getUpdatedAt());
    }

    public record IngredientItem(Long ingredientId, String name, BigDecimal quantity, String unit) {

        static IngredientItem from(RecipeIngredient ri) {
            return new IngredientItem(ri.getIngredient().getId(), ri.getIngredient().getName(),
                    ri.getQuantity(), ri.getUnit());
        }
    }

    /** @param order 1부터 시작하는 단계 번호 */
    public record StepItem(int order, String content, String imageUrl) {

        static StepItem of(int order, RecipeStep step) {
            return new StepItem(order, step.getContent(), step.getImageUrl());
        }
    }

    public record TagItem(Long id, TagType type, String name) {

        static TagItem from(Tag tag) {
            return new TagItem(tag.getId(), tag.getType(), tag.getName());
        }
    }
}
