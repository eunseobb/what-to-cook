package com.whattocook.recipe.dto;

import com.whattocook.recipe.domain.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param mainImageUrl    대표 이미지 URL (업로드는 별도 API)
 * @param cookTimeMinutes 조리 시간(분)
 * @param tagIds          태그 마스터 id 목록
 */
public record RecipeCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "http(s) URL이어야 합니다.") String mainImageUrl,
        @NotNull Difficulty difficulty,
        @NotNull @Min(1) @Max(1440) Integer cookTimeMinutes,
        @NotNull @Min(1) @Max(100) Integer servings,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> ingredients,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeStepRequest> steps,
        @Size(max = 10) List<@NotNull Long> tagIds) {
}
