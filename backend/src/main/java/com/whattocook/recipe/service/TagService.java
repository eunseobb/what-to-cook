package com.whattocook.recipe.service;

import com.whattocook.recipe.domain.TagType;
import com.whattocook.recipe.dto.TagResponse;
import com.whattocook.recipe.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 태그 마스터 조회. 온보딩 선택지와 레시피 태그 선택지로 쓴다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;

    /** @param type 태그 종류. null이면 전체 */
    public List<TagResponse> list(TagType type) {
        return (type == null ? tagRepository.findAllByOrderByIdAsc() : tagRepository.findAllByTypeOrderByIdAsc(type))
                .stream().map(TagResponse::from).toList();
    }
}
