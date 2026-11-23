package com.whattocook.recommendation.repository;

import com.whattocook.recipe.domain.Recipe;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends Repository<Recipe, Long> {

    /**
     * 보유 재료가 하나라도 들어간 게시 중인 커뮤니티 레시피를 일치율 순으로 추린다(후보군 사전 축소).
     * 기본 양념은 일치·필요 재료 수에서 빼고, 사용자의 알레르기 재료가 든 레시피는 제외한다.
     * 보유 재료 → 레시피 탐색은 recipe_ingredient(ingredient_id, recipe_id) 인덱스만으로 끝난다.
     */
    @Query(value = """
            select ri.recipe_id as recipeId,
                   sum(case when ri.ingredient_id in (:ownedIds) then 1 else 0 end) as matchedCount,
                   count(*) as requiredCount
            from recipe_ingredient ri
            join ingredient i on i.id = ri.ingredient_id
            join recipe r on r.id = ri.recipe_id
            where ri.recipe_id in (select o.recipe_id from recipe_ingredient o where o.ingredient_id in (:ownedIds))
              and i.is_pantry_staple = false
              and r.status = 'PUBLISHED'
              and r.source = 'COMMUNITY'
              and not exists (select 1
                              from recipe_ingredient a
                              join user_excluded_ingredient e on e.ingredient_id = a.ingredient_id
                              where a.recipe_id = ri.recipe_id and e.user_id = :userId and e.type = 'ALLERGY')
            group by ri.recipe_id
            having sum(case when ri.ingredient_id in (:ownedIds) then 1 else 0 end) > 0
            order by sum(case when ri.ingredient_id in (:ownedIds) then 1 else 0 end) * 1.0 / count(*) desc,
                     ri.recipe_id desc
            limit :limit
            """, nativeQuery = true)
    List<MatchCandidate> findMatchCandidates(@Param("userId") Long userId,
                                             @Param("ownedIds") Collection<Long> ownedIds,
                                             @Param("limit") int limit);

    interface MatchCandidate {
        Long getRecipeId();

        long getMatchedCount();

        long getRequiredCount();
    }
}
