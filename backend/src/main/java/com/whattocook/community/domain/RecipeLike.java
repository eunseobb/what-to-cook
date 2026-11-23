package com.whattocook.community.domain;

import com.whattocook.global.common.BaseTimeEntity;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.user.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 레시피 좋아요. (user, recipe) 유니크 제약으로 사용자당 1회만 허용한다.
 */
@Getter
@Entity
@Table(name = "recipe_like",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipe_like_user_recipe", columnNames = {"user_id", "recipe_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    public RecipeLike(User user, Recipe recipe) {
        this.user = user;
        this.recipe = recipe;
    }
}
