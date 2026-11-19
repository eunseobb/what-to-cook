package com.whattocook.ingredient.service;

import com.whattocook.ingredient.domain.Ingredient;
import java.util.List;

/**
 * @param matched   매칭된 표준 재료 (입력 순서, 중복 제거)
 * @param unmatched 매칭되지 않은 원본 입력 (앞뒤 공백 제거, 중복 제거)
 */
public record IngredientNormalizationResult(List<Ingredient> matched, List<String> unmatched) {
}
