package com.whattocook.recommendation.repository;

import com.whattocook.recipe.domain.Recipe;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends Repository<Recipe, Long> {

    /**
     * 보유 재료가 하나라도 들어간 게시 중인 레시피를 일치율 순으로 추린다(후보군 사전 축소).
     * 커뮤니티 레시피와, 누군가 좋아요·저장한 AI 레시피(사람이 한 번 검증한 것으로 본다)가 대상이다.
     * 기본 양념은 일치·필요 재료 수에서 빼고, 사용자의 알레르기 재료가 든 레시피는 제외한다.
     * 태그를 주면 그 태그를 모두 가진 레시피만 남긴다(AND). 후보 수 제한 전에 걸러야 태그 결과가 잘리지 않는다.
     * 보유 재료 → 레시피 탐색은 recipe_ingredient(ingredient_id, recipe_id) 인덱스만으로 끝난다.
     *
     * @param tagIds   필터 태그 ID (중복 없이). 없으면 빈 컬렉션
     * @param tagCount tagIds 개수. 0이면 태그로 거르지 않는다
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
              and (r.source = 'COMMUNITY'
                   or exists (select 1 from recipe_like l where l.recipe_id = r.id)
                   or exists (select 1 from recipe_bookmark b where b.recipe_id = r.id))
              and not exists (select 1
                              from recipe_ingredient a
                              join user_excluded_ingredient e on e.ingredient_id = a.ingredient_id
                              where a.recipe_id = ri.recipe_id and e.user_id = :userId and e.type = 'ALLERGY')
              and (:tagCount = 0
                   or (select count(*) from recipe_tag t
                       where t.recipe_id = ri.recipe_id and t.tag_id in (:tagIds)) = :tagCount)
            group by ri.recipe_id
            having sum(case when ri.ingredient_id in (:ownedIds) then 1 else 0 end) > 0
            order by sum(case when ri.ingredient_id in (:ownedIds) then 1 else 0 end) * 1.0 / count(*) desc,
                     ri.recipe_id desc
            limit :limit
            """, nativeQuery = true)
    List<MatchCandidate> findMatchCandidates(@Param("userId") Long userId,
                                             @Param("ownedIds") Collection<Long> ownedIds,
                                             @Param("tagIds") Collection<Long> tagIds,
                                             @Param("tagCount") int tagCount,
                                             @Param("limit") int limit);

    interface MatchCandidate {
        Long getRecipeId();

        long getMatchedCount();

        long getRequiredCount();
    }
}
