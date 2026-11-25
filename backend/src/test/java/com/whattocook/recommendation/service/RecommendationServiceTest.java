package com.whattocook.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.service.IngredientNormalizationResult;
import com.whattocook.ingredient.service.IngredientNormalizationService;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recommendation.dto.RecommendationQuery;
import com.whattocook.recommendation.dto.RecommendationRequest;
import com.whattocook.recommendation.dto.RecommendedRecipeResponse;
import com.whattocook.recommendation.repository.RecommendationRepository;
import com.whattocook.recommendation.repository.RecommendationRepository.MatchCandidate;
import com.whattocook.user.domain.User;
import com.whattocook.user.domain.UserPreferenceTag;
import com.whattocook.user.repository.UserPreferenceTagRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private IngredientNormalizationService normalizationService;
    @Mock
    private RecommendationRepository recommendationRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private UserPreferenceTagRepository preferenceTagRepository;

    private RecommendationService recommendationService;

    private final User user = withId(new User("me@example.com", "hashed", "나"), 1L);
    private final Ingredient egg = withId(Ingredient.builder()
            .name("달걀").category(IngredientCategory.DAIRY_EGG).baseUnit("개").build(), 10L);
    private final Tag korean = withId(new Tag(TagType.CUISINE, "한식"), 100L);

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(normalizationService, recommendationRepository,
                recipeRepository, preferenceTagRepository, new RecommendationProperties(0.6, 0.25, 0.15, 200));
    }

    @Test
    @DisplayName("일치율·취향·인기도 가중합으로 정렬한다")
    void rank() {
        givenCandidates();

        CursorPageResponse<RecommendedRecipeResponse> page =
                recommendationService.recommend(1L, new RecommendationRequest(List.of("계란"), null, null));

        assertThat(page.items()).extracting(r -> r.recipe().id()).containsExactly(2L, 1L, 3L);
        // 1/2 × 0.6 + 취향 1 × 0.25 + 인기도 1 × 0.15
        assertThat(page.items().get(0).score()).isCloseTo(0.7, within(1e-9));
        assertThat(page.items().get(0).matchRate()).isEqualTo(0.5);
        assertThat(page.items().get(1).score()).isCloseTo(0.6, within(1e-9));
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("커서는 순위 오프셋이며 다음 페이지를 이어서 준다")
    void paginate() {
        givenCandidates();

        CursorPageResponse<RecommendedRecipeResponse> first =
                recommendationService.recommend(1L, new RecommendationRequest(List.of("계란"), null, 2));
        CursorPageResponse<RecommendedRecipeResponse> second =
                recommendationService.recommend(1L, new RecommendationRequest(List.of("계란"), first.nextCursor(), 2));

        assertThat(first.items()).extracting(r -> r.recipe().id()).containsExactly(2L, 1L);
        assertThat(first.nextCursor()).isEqualTo("2");
        assertThat(second.items()).extracting(r -> r.recipe().id()).containsExactly(3L);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("표준 재료로 매칭된 입력이 없으면 후보를 조회하지 않고 빈 결과를 준다")
    void noMatchedIngredient() {
        given(normalizationService.normalize(any())).willReturn(new IngredientNormalizationResult(List.of(), List.of("없는재료")));

        CursorPageResponse<RecommendedRecipeResponse> page =
                recommendationService.recommend(1L, new RecommendationRequest(List.of("없는재료"), null, null));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        verify(recommendationRepository, never()).findMatchCandidates(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("쿼리스트링 추천은 재료 ID를 정규화 없이 쓰고, 중복 재료·태그를 합쳐 같은 점수로 정렬한다")
    void recommendByQuery() {
        given(recommendationRepository.findMatchCandidates(1L, List.of(10L), List.of(100L), 1, 200)).willReturn(List.of(
                new Candidate(1L, 1, 2), new Candidate(2L, 2, 2)));
        given(recipeRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(
                recipe(1L, 0, List.of(korean)), recipe(2L, 0, List.of(korean))));

        CursorPageResponse<RecommendedRecipeResponse> page = recommendationService.recommend(1L,
                new RecommendationQuery(List.of(10L, 10L), List.of(100L, 100L), null, null));

        assertThat(page.items()).extracting(r -> r.recipe().id()).containsExactly(2L, 1L);
        verify(normalizationService, never()).normalize(any());
    }

    private void givenCandidates() {
        given(normalizationService.normalize(any())).willReturn(new IngredientNormalizationResult(List.of(egg), List.of()));
        given(recommendationRepository.findMatchCandidates(1L, List.of(10L), List.of(), 0, 200)).willReturn(List.of(
                new Candidate(1L, 2, 2), new Candidate(2L, 1, 2), new Candidate(3L, 1, 2)));
        given(recipeRepository.findAllById(List.of(1L, 2L, 3L))).willReturn(List.of(
                recipe(1L, 0, List.of()), recipe(2L, 9, List.of(korean)), recipe(3L, 0, List.of())));
        given(preferenceTagRepository.findAllByUserIdOrderByTagIdAsc(1L))
                .willReturn(List.of(new UserPreferenceTag(user, korean, 1.0)));
    }

    private Recipe recipe(Long id, int likeCount, List<Tag> tags) {
        Recipe recipe = withId(Recipe.builder()
                .author(user).source(RecipeSource.COMMUNITY).title("레시피" + id)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1).tags(tags)
                .build(), id);
        ReflectionTestUtils.setField(recipe, "likeCount", likeCount);
        return recipe;
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private record Candidate(Long getRecipeId, long getMatchedCount, long getRequiredCount) implements MatchCandidate {
    }
}
