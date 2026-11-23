package com.whattocook.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param ingredients 보유 재료 입력. 별칭("파")도 표준 재료로 정규화한다.
 * @param cursor      이전 페이지의 nextCursor (첫 페이지는 생략)
 * @param size        페이지 크기 (기본 20)
 */
public record RecommendationRequest(
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 50) String> ingredients,
        @Pattern(regexp = "\\d{1,4}") String cursor,
        @Min(1) @Max(50) Integer size) {

    public RecommendationRequest {
        size = size == null ? 20 : size;
    }
}
