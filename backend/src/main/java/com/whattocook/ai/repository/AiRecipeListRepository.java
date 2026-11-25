package com.whattocook.ai.repository;

import com.whattocook.recipe.domain.Recipe;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AiRecipeListRepository extends Repository<Recipe, Long> {

    /**
     * 게시 중인 AI 생성 레시피를 id 내림차순(최신순) 커서로 조회한다.
     * userId가 있으면 그 사용자의 알레르기 재료가 든 레시피를 뺀다. 비선호 재료는 거르지 않는다.
     */
    @EntityGraph(attributePaths = "author")
    @Query("""
            select r from Recipe r
            where r.source = com.whattocook.recipe.domain.RecipeSource.AI_GENERATED
              and r.status = com.whattocook.recipe.domain.RecipeStatus.PUBLISHED
              and r.id < :cursorId
              and (:userId is null or not exists (
                    select 1 from Recipe a join a.ingredients ai, UserExcludedIngredient e
                    where a.id = r.id and e.ingredient = ai.ingredient and e.user.id = :userId
                      and e.type = com.whattocook.user.domain.ExclusionType.ALLERGY))
            order by r.id desc
            """)
    List<Recipe> findAiRecipes(@Param("userId") Long userId, @Param("cursorId") Long cursorId, Limit limit);
}
