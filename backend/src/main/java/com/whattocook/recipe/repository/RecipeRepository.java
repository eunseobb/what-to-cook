package com.whattocook.recipe.repository;

import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    Optional<Recipe> findByIdAndStatus(Long id, RecipeStatus status);

    /** 행 잠금(SELECT ... FOR UPDATE)으로 조회한다. 같은 레시피 신고를 직렬화해 누적 수를 정확히 센다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recipe r where r.id = :id")
    Optional<Recipe> findByIdForUpdate(@Param("id") Long id);

    /** 커서(id) 기반 최신순 목록. id 내림차순이라 PK 인덱스로 범위 조회한다. */
    List<Recipe> findByStatusAndIdLessThanOrderByIdDesc(RecipeStatus status, Long cursorId, Limit limit);
}
