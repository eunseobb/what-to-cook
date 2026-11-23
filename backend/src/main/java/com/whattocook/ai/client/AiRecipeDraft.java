package com.whattocook.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whattocook.recipe.domain.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * LLM이 Structured Output(JSON Schema)으로 돌려주는 레시피 초안. 저장 전에 검증한다.
 * OpenAI strict 모드는 모든 필드가 required여야 해서 @JsonProperty(required = true)를 단다.
 */
public record AiRecipeDraft(
        @JsonProperty(required = true) @NotBlank @Size(max = 100) String title,
        @JsonProperty(required = true) @NotNull Difficulty difficulty,
        @JsonProperty(required = true) @Min(1) @Max(1440) int cookTimeMinutes,
        @JsonProperty(required = true) @Min(1) @Max(20) int servings,
        @JsonProperty(required = true) @NotEmpty @Size(max = 30) List<@NotNull @Valid Item> ingredients,
        @JsonProperty(required = true) @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 1000) String> steps) {

    /**
     * @param name     재료 표준 이름 (예: 대파, 달걀)
     * @param quantity 분량
     * @param unit     단위 (예: g, ml, 개, 큰술)
     */
    public record Item(
            @JsonProperty(required = true) @NotBlank @Size(max = 50) String name,
            @JsonProperty(required = true) @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @JsonProperty(required = true) @NotBlank @Size(max = 10) String unit) {
    }

    /** OpenAI JSON Schema 응답은 최상위가 객체여야 해서 목록을 감싼다. */
    public record Drafts(@JsonProperty(required = true) List<AiRecipeDraft> recipes) {
    }
}
