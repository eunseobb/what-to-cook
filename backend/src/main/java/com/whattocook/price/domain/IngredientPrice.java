package com.whattocook.price.domain;

import com.whattocook.global.common.BaseTimeEntity;
import com.whattocook.ingredient.domain.Ingredient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재료의 기준 단위(Ingredient.baseUnit)당 가격. 출처와 기준 시각을 함께 저장해 "약 ○○원"과 함께 보여준다.
 */
@Getter
@Entity
@Table(name = "ingredient_price", uniqueConstraints = @UniqueConstraint(
        name = "uk_ingredient_price_ingredient_source_referenced_at",
        columnNames = {"ingredient_id", "source", "referenced_at"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientPrice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(name = "price_per_base_unit", nullable = false, precision = 12, scale = 4)
    private BigDecimal pricePerBaseUnit;

    @Column(name = "referenced_at", nullable = false)
    private LocalDateTime referencedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceSource source;

    public IngredientPrice(Ingredient ingredient, BigDecimal pricePerBaseUnit, LocalDateTime referencedAt,
                           PriceSource source) {
        this.ingredient = ingredient;
        this.pricePerBaseUnit = pricePerBaseUnit;
        this.referencedAt = referencedAt;
        this.source = source;
    }
}
