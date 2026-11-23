package com.whattocook.user.repository;

import com.whattocook.user.domain.UserExcludedIngredient;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserExcludedIngredientRepository extends JpaRepository<UserExcludedIngredient, Long> {

    @EntityGraph(attributePaths = "ingredient")
    List<UserExcludedIngredient> findAllByUserIdOrderByIngredientIdAsc(Long userId);

    /** 온보딩 전체 교체용. 행마다 select·delete하지 않고 한 번에 지운다. */
    @Modifying
    @Query("delete from UserExcludedIngredient e where e.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
