package com.whattocook.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recommendation.dto.PersonalizedRecipeResponse;
import com.whattocook.recommendation.repository.PersonalizedRecommendationRepository;
import com.whattocook.recommendation.repository.PersonalizedRecommendationRepository.TasteCandidate;
import com.whattocook.user.domain.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PersonalizedRecommendationServiceTest {

    @Mock
    private PersonalizedRecommendationRepository personalizedRepository;
    @Mock
    private RecipeRepository recipeRepository;

    private PersonalizedRecommendationService service;

    private final User user = withId(new User("me@example.com", "hashed", "나"), 1L);

    @BeforeEach
    void setUp() {
        service = new PersonalizedRecommendationService(personalizedRepository, recipeRepository,
                new RecommendationProperties(0.6, 0.25, 0.15, 200));
        given(personalizedRepository.findTasteCandidates(1L, 200)).willReturn(List.of(
                new Candidate(1L, 1.5), new Candidate(3L, 0.75), new Candidate(2L, 0)));
        given(recipeRepository.findAllById(List.of(1L, 3L, 2L))).willReturn(List.of(
                recipe(1L, 0), recipe(2L, 9), recipe(3L, 0)));
    }

    @Test
    @DisplayName("취향·인기도 가중합으로 정렬하고 일치율 가중치는 쓰지 않는다")
    void rank() {
        CursorPageResponse<PersonalizedRecipeResponse> page = service.recommend(1L, null, 20);

        assertThat(page.items()).extracting(r -> r.recipe().id()).containsExactly(1L, 2L, 3L);
        // 취향 1 × 0.25, 인기도 1 × 0.15, 취향 0.5 × 0.25
        assertThat(page.items()).extracting(PersonalizedRecipeResponse::score)
                .containsExactly(0.25, 0.15, 0.125);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("커서는 순위 오프셋이며 다음 페이지를 이어서 준다")
    void paginate() {
        CursorPageResponse<PersonalizedRecipeResponse> first = service.recommend(1L, null, 2);
        CursorPageResponse<PersonalizedRecipeResponse> second = service.recommend(1L, first.nextCursor(), 2);

        assertThat(first.items()).extracting(r -> r.recipe().id()).containsExactly(1L, 2L);
        assertThat(first.nextCursor()).isEqualTo("2");
        assertThat(second.items()).extracting(r -> r.recipe().id()).containsExactly(3L);
        assertThat(second.hasNext()).isFalse();
    }

    private Recipe recipe(Long id, int likeCount) {
        Recipe recipe = withId(Recipe.builder()
                .author(user).source(RecipeSource.COMMUNITY).title("레시피" + id)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .build(), id);
        ReflectionTestUtils.setField(recipe, "likeCount", likeCount);
        return recipe;
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private record Candidate(Long getRecipeId, double getTasteScore) implements TasteCandidate {
    }
}
