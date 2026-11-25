package com.whattocook.recipe.repository;

import com.whattocook.recipe.domain.Recipe;
import java.util.List;

public interface RecipeRepositoryCustom {

    /**
     * 태그·기간 필터와 정렬을 적용한 게시 중 레시피 목록. 작성자를 함께 가져온다.
     *
     * @param cursor 이전 페이지 마지막 항목의 정렬 키. 첫 페이지는 null
     */
    List<Recipe> search(RecipeListCondition condition, RecipeCursor cursor, int limit);
}
