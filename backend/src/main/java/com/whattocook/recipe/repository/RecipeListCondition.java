package com.whattocook.recipe.repository;

import com.whattocook.recipe.domain.RecipeSort;
import com.whattocook.recipe.domain.RecipeSource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 레시피 목록 조회 조건. 게시 상태(PUBLISHED)만 조회한다.
 *
 * @param tagIds        모두 가진 레시피만 (AND). 비어 있으면 필터 없음
 * @param sort          정렬
 * @param createdAfter  이 시각 이후 작성된 레시피만. null이면 기간 제한 없음
 * @param source        이 출처의 레시피만. null이면 전체
 * @param allergyUserId 이 사용자의 알레르기 재료가 든 레시피를 뺀다(비선호는 거르지 않음). null이면 필터 없음
 */
public record RecipeListCondition(List<Long> tagIds, RecipeSort sort, LocalDateTime createdAfter,
                                  RecipeSource source, Long allergyUserId) {

    public RecipeListCondition {
        tagIds = tagIds == null ? List.of() : tagIds.stream().distinct().toList();
        sort = sort == null ? RecipeSort.LATEST : sort;
    }
}
