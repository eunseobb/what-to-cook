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

/** 레시피 저장(북마크). (user, recipe)로 유일하다. */
@Getter
@Entity
@Table(name = "recipe_bookmark",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipe_bookmark_user_recipe",
                columnNames = {"user_id", "recipe_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeBookmark extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    public RecipeBookmark(User user, Recipe recipe) {
        this.user = user;
        this.recipe = recipe;
    }
}
