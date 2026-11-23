package com.whattocook.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** @param ingredients 보유 재료 입력. 별칭("파")도 표준 재료로 정규화한다. */
public record AiRecommendationRequest(
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 50) String> ingredients) {
}
