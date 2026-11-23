package com.whattocook.community.repository;

import com.whattocook.community.domain.RecipeBookmark;
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
}
