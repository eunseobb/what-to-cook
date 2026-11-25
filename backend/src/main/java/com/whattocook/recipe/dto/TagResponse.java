package com.whattocook.recipe.dto;

import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;

/** colorKey는 프론트 디자인 토큰 이름이다(accent-sage → --color-accent-sage). */
public record TagResponse(Long id, TagType type, String name, String colorKey) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getType(), tag.getName(), tag.getColorKey());
    }
}
