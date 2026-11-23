package com.whattocook.user.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알레르기·비선호 재료. 알레르기 재료가 들어간 레시피만 추천에서 하드 필터로 뺀다. */
@Getter
@Entity
@Table(name = "user_excluded_ingredient")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserExcludedIngredient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExclusionType type;

    public UserExcludedIngredient(User user, Ingredient ingredient, ExclusionType type) {
        this.user = user;
        this.ingredient = ingredient;
        this.type = type;
    }
}
