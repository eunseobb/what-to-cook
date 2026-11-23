package com.whattocook.price.repository;

import com.whattocook.price.domain.IngredientPrice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientPriceRepository extends JpaRepository<IngredientPrice, Long> {
}
