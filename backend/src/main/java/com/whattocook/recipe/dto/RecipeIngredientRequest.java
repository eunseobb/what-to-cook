package com.whattocook.recipe.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * @param ingredientId 표준 재료 id (자동완성 결과)
 * @param quantity     분량. "약간"처럼 정량이 없으면 비운다.
 * @param unit         단위 (예: g, 개, 큰술)
 */
public record RecipeIngredientRequest(
        @NotNull Long ingredientId,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal quantity,
        @Size(max = 10) String unit) {
}
