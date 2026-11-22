package com.whattocook.recipe.repository;

import com.whattocook.recipe.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
}
