package com.whattocook.user.dto;

import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.recipe.dto.TagResponse;
import java.util.List;

public record OnboardingResponse(
        List<TagResponse> preferenceTags,
        List<IngredientItem> allergyIngredients,
        List<IngredientItem> dislikeIngredients) {

    public record IngredientItem(Long id, String name) {

        public static IngredientItem from(Ingredient ingredient) {
            return new IngredientItem(ingredient.getId(), ingredient.getName());
        }
    }
}
