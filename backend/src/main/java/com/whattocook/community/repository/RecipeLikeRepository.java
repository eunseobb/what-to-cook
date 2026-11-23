package com.whattocook.community.repository;

import com.whattocook.community.domain.RecipeLike;
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
}
