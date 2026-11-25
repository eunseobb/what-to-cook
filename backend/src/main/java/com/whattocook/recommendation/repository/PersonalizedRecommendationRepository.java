package com.whattocook.recommendation.repository;

import com.whattocook.recipe.domain.Recipe;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 재료 입력 없는 "내 취향 추천" 후보 조회. */
public interface PersonalizedRecommendationRepository extends Repository<Recipe, Long> {

    /**
     * 게시 중인 추천 대상 레시피를 취향 점수(사용자 선호 태그 가중치 합), 좋아요 수 순으로 추린다.
     * 대상(커뮤니티 + 좋아요·저장받은 AI 레시피)과 알레르기 하드 필터는 재료 기반 추천(findMatchCandidates)과 같다.
     * shortcut: 취향 점수 상위 limit건만 보므로 후보가 넘치면 취향 태그 없는 인기 레시피가 빠진다. 레시피가 많아지면 인기 상위 후보를 합친다.
     * shortcut: 게시 레시피 전체를 그룹핑한다. 10만 건 부하 테스트에서 느리면 태그·인기 인덱스로 후보를 먼저 줄인다.
     */
    @Query(value = """
            select r.id as recipeId,
                   coalesce(sum(p.weight), 0) as tasteScore
            from recipe r
            left join recipe_tag rt on rt.recipe_id = r.id
            left join user_preference_tag p on p.tag_id = rt.tag_id and p.user_id = :userId
            where r.status = 'PUBLISHED'
              and (r.source = 'COMMUNITY'
                   or exists (select 1 from recipe_like l where l.recipe_id = r.id)
                   or exists (select 1 from recipe_bookmark b where b.recipe_id = r.id))
              and not exists (select 1
                              from recipe_ingredient a
                              join user_excluded_ingredient e on e.ingredient_id = a.ingredient_id
                              where a.recipe_id = r.id and e.user_id = :userId and e.type = 'ALLERGY')
            group by r.id, r.like_count
            order by coalesce(sum(p.weight), 0) desc, r.like_count desc, r.id desc
            limit :limit
            """, nativeQuery = true)
    List<TasteCandidate> findTasteCandidates(@Param("userId") Long userId, @Param("limit") int limit);

    interface TasteCandidate {
        Long getRecipeId();

        double getTasteScore();
    }
}
