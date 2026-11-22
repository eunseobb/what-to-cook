package com.whattocook.recipe.dto;

import com.whattocook.recipe.domain.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 부분 수정. null인 필드는 바꾸지 않고, 목록 필드는 보내면 통째로 교체한다.
 *
 * @param mainImageUrl 빈 문자열이면 대표 이미지를 지운다.
 */
public record RecipeUpdateRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "공백일 수 없습니다.") String title,
        @Size(max = 500) @Pattern(regexp = "^(https?://\\S+)?$", message = "http(s) URL이어야 합니다.") String mainImageUrl,
        Difficulty difficulty,
        @Min(1) @Max(1440) Integer cookTimeMinutes,
        @Min(1) @Max(100) Integer servings,
        @Size(min = 1, max = 50) List<@NotNull @Valid RecipeIngredientRequest> ingredients,
        @Size(min = 1, max = 50) List<@NotNull @Valid RecipeStepRequest> steps,
        @Size(max = 10) List<@NotNull Long> tagIds) {
}
