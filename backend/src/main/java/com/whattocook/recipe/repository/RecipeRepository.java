package com.whattocook.recipe.repository;

import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    Optional<Recipe> findByIdAndStatus(Long id, RecipeStatus status);

    /** 커서(id) 기반 최신순 목록. id 내림차순이라 PK 인덱스로 범위 조회한다. */
    List<Recipe> findByStatusAndIdLessThanOrderByIdDesc(RecipeStatus status, Long cursorId, Limit limit);

    boolean existsByIdAndStatus(Long id, RecipeStatus status);

    /** 비관적 락 방식 좋아요: SELECT ... FOR UPDATE로 레시피 행을 잡아 같은 레시피 요청을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recipe r where r.id = :id and r.status = :status")
    Optional<Recipe> findForUpdateByIdAndStatus(Long id, RecipeStatus status);

    /** Redis에 모인 좋아요 증감분을 DB에 더한다. */
    @Modifying
    @Query("update Recipe r set r.likeCount = r.likeCount + :delta where r.id = :id")
    int addLikeCount(Long id, int delta);
}
