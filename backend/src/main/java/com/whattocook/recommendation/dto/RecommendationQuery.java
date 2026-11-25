package com.whattocook.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 검색 화면 URL과 같은 쿼리스트링 추천 조건. 예: {@code ?ingredient=1&ingredient=5&tag=3&tag=7}
 *
 * @param ingredient 보유 표준 재료 ID (자동완성 결과의 ID)
 * @param tag        필터 태그 ID. 모두 가진 레시피만 남긴다(AND). 생략 가능
 * @param cursor     이전 페이지의 nextCursor (첫 페이지는 생략)
 * @param size       페이지 크기 (기본 20)
 */
public record RecommendationQuery(
        @NotEmpty @Size(max = 30) List<@NotNull Long> ingredient,
        @Size(max = 10) List<@NotNull Long> tag,
        @Pattern(regexp = "\\d{1,4}") String cursor,
        @Min(1) @Max(50) Integer size) {

    public RecommendationQuery {
        tag = tag == null ? List.of() : tag;
        size = size == null ? 20 : size;
    }
}
