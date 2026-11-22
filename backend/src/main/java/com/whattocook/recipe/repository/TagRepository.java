package com.whattocook.recipe.repository;

import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByOrderByIdAsc();

    List<Tag> findAllByTypeOrderByIdAsc(TagType type);
}
