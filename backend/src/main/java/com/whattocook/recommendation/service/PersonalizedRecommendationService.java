package com.whattocook.recommendation.service;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recommendation.dto.PersonalizedRecipeResponse;
import com.whattocook.recommendation.repository.PersonalizedRecommendationRepository;
import com.whattocook.recommendation.repository.PersonalizedRecommendationRepository.TasteCandidate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재료 입력 없는 "내 취향 추천". 점수 = 취향 점수 × w2 + 인기도 × w3 (가중치는 재료 기반 추천 설정을 그대로 쓴다).
 * 취향·인기도는 후보 안에서 최댓값으로 나눠 0~1로 맞춘다. 알레르기 재료는 후보 조회에서 하드 필터로 뺀다.
 * 행동 기반 가중치(F3-2) 전에는 온보딩 선호 태그만 취향 점수에 반영된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalizedRecommendationService {

    private final PersonalizedRecommendationRepository personalizedRepository;
    private final RecipeRepository recipeRepository;
    private final RecommendationProperties properties;

    /**
     * @param cursor 이전 페이지의 nextCursor (순위 오프셋, 첫 페이지는 null)
     */
    public CursorPageResponse<PersonalizedRecipeResponse> recommend(Long userId, String cursor, int size) {
        List<TasteCandidate> candidates = personalizedRepository.findTasteCandidates(userId, properties.candidateLimit());
        Map<Long, Recipe> recipeById = recipeRepository.findAllById(
                        candidates.stream().map(TasteCandidate::getRecipeId).toList()).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity()));
        List<TasteCandidate> present = candidates.stream().filter(c -> recipeById.containsKey(c.getRecipeId())).toList();

        double maxTaste = present.stream().mapToDouble(TasteCandidate::getTasteScore).max().orElse(0);
        double maxLikes = present.stream().mapToInt(c -> recipeById.get(c.getRecipeId()).getLikeCount()).max().orElse(0);

        List<PersonalizedRecipeResponse> ranked = present.stream()
                .map(c -> {
                    Recipe recipe = recipeById.get(c.getRecipeId());
                    double taste = maxTaste > 0 ? c.getTasteScore() / maxTaste : 0;
                    // 재료 기반 추천과 같이 좋아요 수는 로그로 눌러 소수 레시피가 독식하지 않게 한다.
                    double popularity = maxLikes > 0 ? Math.log1p(recipe.getLikeCount()) / Math.log1p(maxLikes) : 0;
                    double score = taste * properties.tasteWeight() + popularity * properties.popularityWeight();
                    return new PersonalizedRecipeResponse(RecipeSummaryResponse.from(recipe), score);
                })
                .sorted(Comparator.comparingDouble(PersonalizedRecipeResponse::score).reversed()
                        .thenComparing(r -> r.recipe().id(), Comparator.reverseOrder()))
                .toList();

        // shortcut: 커서는 순위 오프셋이라 페이지 사이에 점수가 바뀌면 중복·누락이 날 수 있다. 결과를 캐시할 때 스냅샷 키로 바꾼다.
        int offset = cursor == null ? 0 : Integer.parseInt(cursor);
        List<PersonalizedRecipeResponse> fetched = ranked.subList(Math.min(offset, ranked.size()),
                Math.min(offset + size + 1, ranked.size()));
        return CursorPageResponse.of(fetched, size, last -> String.valueOf(offset + size));
    }
}
