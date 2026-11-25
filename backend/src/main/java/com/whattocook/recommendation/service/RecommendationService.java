package com.whattocook.recommendation.service;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.service.IngredientNormalizationService;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recommendation.dto.RecommendationRequest;
import com.whattocook.recommendation.dto.RecommendedRecipeResponse;
import com.whattocook.recommendation.repository.RecommendationRepository;
import com.whattocook.recommendation.repository.RecommendationRepository.MatchCandidate;
import com.whattocook.user.domain.UserPreferenceTag;
import com.whattocook.user.repository.UserPreferenceTagRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재료 기반 커뮤니티 레시피 추천.
 * 정렬 점수 = 일치율 × w1 + 취향 점수 × w2 + 인기도 × w3. 취향·인기도는 후보 안에서 최댓값으로 나눠 0~1로 맞춘다.
 * 알레르기 재료는 후보 조회에서 하드 필터로 빼고, 비선호 재료는 점수에 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final IngredientNormalizationService normalizationService;
    private final RecommendationRepository recommendationRepository;
    private final RecipeRepository recipeRepository;
    private final UserPreferenceTagRepository preferenceTagRepository;
    private final RecommendationProperties properties;

    public CursorPageResponse<RecommendedRecipeResponse> recommend(Long userId, RecommendationRequest request) {
        List<Long> ownedIds = normalizationService.normalize(request.ingredients()).matched().stream()
                .map(Ingredient::getId).toList();
        List<MatchCandidate> candidates = ownedIds.isEmpty() ? List.of()
                : recommendationRepository.findMatchCandidates(userId, ownedIds, List.of(), 0, properties.candidateLimit());
        if (candidates.isEmpty()) {
            return new CursorPageResponse<>(List.of(), null, false);
        }

        Map<Long, Recipe> recipeById = recipeRepository.findAllById(
                        candidates.stream().map(MatchCandidate::getRecipeId).toList()).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Map<Long, Double> weightByTagId = preferenceTagRepository.findAllByUserIdOrderByTagIdAsc(userId).stream()
                .collect(Collectors.toMap(p -> p.getTag().getId(), UserPreferenceTag::getWeight));

        List<MatchCandidate> present = candidates.stream().filter(c -> recipeById.containsKey(c.getRecipeId())).toList();
        Map<Long, Double> tasteById = present.stream().collect(Collectors.toMap(MatchCandidate::getRecipeId,
                c -> recipeById.get(c.getRecipeId()).getTags().stream()
                        .mapToDouble(t -> weightByTagId.getOrDefault(t.getId(), 0.0)).sum()));
        double maxTaste = tasteById.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double maxLikes = present.stream().mapToInt(c -> recipeById.get(c.getRecipeId()).getLikeCount()).max().orElse(0);

        List<RecommendedRecipeResponse> ranked = present.stream()
                .map(c -> {
                    Recipe recipe = recipeById.get(c.getRecipeId());
                    double matchRate = (double) c.getMatchedCount() / c.getRequiredCount();
                    double taste = maxTaste > 0 ? tasteById.get(recipe.getId()) / maxTaste : 0;
                    // 좋아요 수는 소수 레시피에 몰리므로 로그로 눌러 일치율을 압도하지 않게 한다.
                    double popularity = maxLikes > 0 ? Math.log1p(recipe.getLikeCount()) / Math.log1p(maxLikes) : 0;
                    double score = matchRate * properties.matchWeight() + taste * properties.tasteWeight()
                            + popularity * properties.popularityWeight();
                    return new RecommendedRecipeResponse(RecipeSummaryResponse.from(recipe),
                            (int) c.getMatchedCount(), (int) c.getRequiredCount(), matchRate, score);
                })
                .sorted(Comparator.comparingDouble(RecommendedRecipeResponse::score).reversed()
                        .thenComparing(r -> r.recipe().id(), Comparator.reverseOrder()))
                .toList();

        // shortcut: 커서는 순위 오프셋이라 페이지 사이에 점수가 바뀌면 중복·누락이 날 수 있다. 결과를 캐시할 때 스냅샷 키로 바꾼다.
        int offset = request.cursor() == null ? 0 : Integer.parseInt(request.cursor());
        int size = request.size();
        List<RecommendedRecipeResponse> fetched = ranked.subList(Math.min(offset, ranked.size()),
                Math.min(offset + size + 1, ranked.size()));
        return CursorPageResponse.of(fetched, size, last -> String.valueOf(offset + size));
    }
}
