package com.whattocook.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param imageUrl 업로드가 끝난 이미지 URL (업로드는 별도 API)
 */
public record RecipeStepRequest(
        @NotBlank @Size(max = 1000) String content,
        @Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "http(s) URL이어야 합니다.") String imageUrl) {
}
