package com.whattocook.ai.service;

import com.whattocook.ai.repository.AiRecipeListRepository;
import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메인 "AI" 섹션과 전체 보기용 AI 생성 레시피 목록. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRecipeListService {

    private final AiRecipeListRepository aiRecipeListRepository;

    /** @param userId 비로그인이면 null. 로그인 사용자는 알레르기 재료 레시피를 뺀다. */
    public CursorPageResponse<RecipeSummaryResponse> list(Long userId, Long cursor, int size) {
        List<RecipeSummaryResponse> fetched = aiRecipeListRepository.findAiRecipes(
                        userId, cursor == null ? Long.MAX_VALUE : cursor, Limit.of(size + 1))
                .stream().map(RecipeSummaryResponse::from).toList();
        return CursorPageResponse.of(fetched, size, r -> String.valueOf(r.id()));
    }
}
