package com.whattocook.recipe.repository;

import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    Optional<Recipe> findByIdAndStatus(Long id, RecipeStatus status);

    /** 커서(id) 기반 최신순 목록. id 내림차순이라 PK 인덱스로 범위 조회한다. */
    List<Recipe> findByStatusAndIdLessThanOrderByIdDesc(RecipeStatus status, Long cursorId, Limit limit);
}
