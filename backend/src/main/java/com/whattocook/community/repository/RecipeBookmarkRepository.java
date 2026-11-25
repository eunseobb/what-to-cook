package com.whattocook.community.repository;

import com.whattocook.community.domain.RecipeBookmark;
import com.whattocook.recipe.domain.RecipeStatus;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecipeBookmarkRepository extends JpaRepository<RecipeBookmark, Long> {

    /** 이미 저장돼 있으면 아무것도 하지 않는다. 동시 요청이 와도 유니크 제약 위반 없이 한 행만 남는다. */
    @Modifying
    @Query(value = """
            INSERT INTO recipe_bookmark (user_id, recipe_id, created_at, updated_at)
            VALUES (:userId, :recipeId, NOW(), NOW())
            ON CONFLICT (user_id, recipe_id) DO NOTHING""", nativeQuery = true)
    int insertIfAbsent(Long userId, Long recipeId);

    @Modifying
    @Query("DELETE FROM RecipeBookmark b WHERE b.user.id = :userId AND b.recipe.id = :recipeId")
    int deleteByUserIdAndRecipeId(Long userId, Long recipeId);

    /** 마이페이지 저장 목록. 저장한 순서(최신순) 커서 조회이며, 레시피·작성자를 함께 가져온다. */
    @Query("""
            SELECT b FROM RecipeBookmark b JOIN FETCH b.recipe r LEFT JOIN FETCH r.author
            WHERE b.user.id = :userId AND r.status = :status AND b.id < :cursorId
            ORDER BY b.id DESC""")
    List<RecipeBookmark> findPageByUserId(Long userId, RecipeStatus status, Long cursorId, Limit limit);
}
