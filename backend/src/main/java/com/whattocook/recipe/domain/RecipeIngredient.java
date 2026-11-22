package com.whattocook.recipe.domain;

import com.whattocook.ingredient.domain.Ingredient;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 레시피 재료: 표준 재료 + 분량. 분량·단위가 없으면 "약간"처럼 정량이 없는 재료로 본다. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeIngredient {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(length = 10)
    private String unit;

    public RecipeIngredient(Ingredient ingredient, BigDecimal quantity, String unit) {
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit == null || unit.isBlank() ? null : unit.strip();
    }
}
