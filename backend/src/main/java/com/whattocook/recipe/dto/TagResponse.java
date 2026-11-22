package com.whattocook.recipe.dto;

import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;

public record TagResponse(Long id, TagType type, String name) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getType(), tag.getName());
    }
}
