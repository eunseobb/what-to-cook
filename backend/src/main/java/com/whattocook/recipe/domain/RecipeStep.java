package com.whattocook.recipe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 조리 단계. 순서는 Recipe.steps의 순서 컬럼(step_order)이 정한다. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeStep {

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    public RecipeStep(String content, String imageUrl) {
        this.content = content.strip();
        this.imageUrl = imageUrl;
    }
}
