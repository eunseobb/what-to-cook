package com.whattocook.ingredient.repository;

import com.whattocook.ingredient.domain.Ingredient;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    /** 정규화된 별칭 중 하나라도 일치하는 재료를 별칭과 함께 조회한다. */
    @Query("select distinct i from Ingredient i join fetch i.aliases where i.id in "
            + "(select i2.id from Ingredient i2 join i2.aliases a where a in :aliases)")
    List<Ingredient> findAllByAliasIn(@Param("aliases") Collection<String> aliases);
}
