package com.whattocook.community.repository;

import com.whattocook.community.domain.RecipeLike;
import com.whattocook.recipe.domain.RecipeStatus;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RecipeLikeRepository extends JpaRepository<RecipeLike, Long> {

    boolean existsByUserIdAndRecipeId(Long userId, Long recipeId);

    /** @return 지운 행 수(0 또는 1). 엔티티를 읽지 않고 한 번에 지운다. */
    @Transactional
    @Modifying
    @Query("delete from RecipeLike l where l.user.id = :userId and l.recipe.id = :recipeId")
    int deleteByUserIdAndRecipeId(Long userId, Long recipeId);

    /** 마이페이지 좋아요 목록. 좋아요한 순서(최신순) 커서 조회이며, 레시피·작성자를 함께 가져온다. */
    @Query("""
            select l from RecipeLike l join fetch l.recipe r left join fetch r.author
            where l.user.id = :userId and r.status = :status and l.id < :cursorId
            order by l.id desc""")
    List<RecipeLike> findPageByUserId(Long userId, RecipeStatus status, Long cursorId, Limit limit);
}
