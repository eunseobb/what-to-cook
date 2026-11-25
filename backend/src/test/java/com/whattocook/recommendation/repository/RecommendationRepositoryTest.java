package com.whattocook.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.whattocook.community.domain.RecipeBookmark;
import com.whattocook.community.domain.RecipeLike;
import com.whattocook.community.repository.RecipeBookmarkRepository;
import com.whattocook.community.repository.RecipeLikeRepository;
import com.whattocook.global.config.JpaConfig;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recommendation.repository.RecommendationRepository.MatchCandidate;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.ExclusionType;
import com.whattocook.user.domain.User;
import com.whattocook.user.domain.UserExcludedIngredient;
import com.whattocook.user.repository.UserExcludedIngredientRepository;
import com.whattocook.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
class RecommendationRepositoryTest {

    @Autowired
    private RecommendationRepository recommendationRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserExcludedIngredientRepository excludedIngredientRepository;
    @Autowired
    private RecipeLikeRepository recipeLikeRepository;
    @Autowired
    private RecipeBookmarkRepository recipeBookmarkRepository;
    @Autowired
    private EntityManager em;

    private User user;
    private Ingredient egg;
    private Ingredient onion;
    private Ingredient tomato;
    private Ingredient peanut;
    private Ingredient soySauce;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("recommend@example.com", "hashed", "추천"));
        egg = ingredient("달걀", false);
        onion = ingredient("양파", false);
        tomato = ingredient("토마토", false);
        peanut = ingredient("땅콩", false);
        soySauce = ingredient("간장", true);
        excludedIngredientRepository.save(new UserExcludedIngredient(user, peanut, ExclusionType.ALLERGY));
        excludedIngredientRepository.save(new UserExcludedIngredient(user, tomato, ExclusionType.DISLIKE));
    }

    @Test
    @DisplayName("보유 재료가 든 커뮤니티 레시피를 일치율 순으로 추리고, 기본 양념은 세지 않으며 알레르기 재료 레시피는 뺀다")
    void findMatchCandidates() {
        Recipe full = recipe("달걀양파볶음", RecipeSource.COMMUNITY, egg, onion, soySauce);
        Recipe half = recipe("토마토달걀볶음", RecipeSource.COMMUNITY, egg, tomato);
        recipe("땅콩달걀", RecipeSource.COMMUNITY, egg, peanut);
        recipe("토마토샐러드", RecipeSource.COMMUNITY, tomato);
        recipe("간장소스", RecipeSource.COMMUNITY, soySauce);
        recipe("AI 달걀양파", RecipeSource.AI_GENERATED, egg, onion);
        recipe("삭제된 달걀요리", RecipeSource.COMMUNITY, egg).delete();
        em.flush();

        List<MatchCandidate> candidates = recommendationRepository.findMatchCandidates(
                user.getId(), List.of(egg.getId(), onion.getId(), soySauce.getId()), 10);

        assertThat(candidates)
                .extracting(MatchCandidate::getRecipeId, MatchCandidate::getMatchedCount, MatchCandidate::getRequiredCount)
                .containsExactly(tuple(full.getId(), 2L, 2L), tuple(half.getId(), 1L, 2L));
    }

    @Test
    @DisplayName("AI 레시피는 좋아요나 저장을 받은 것만 후보에 넣는다")
    void aiRecipesNeedLikeOrBookmark() {
        Recipe liked = recipe("좋아요 받은 AI", RecipeSource.AI_GENERATED, egg, onion);
        Recipe bookmarked = recipe("저장된 AI", RecipeSource.AI_GENERATED, egg);
        recipe("반응 없는 AI", RecipeSource.AI_GENERATED, egg);
        recipeLikeRepository.save(new RecipeLike(user, liked));
        recipeBookmarkRepository.save(new RecipeBookmark(user, bookmarked));
        em.flush();

        assertThat(recommendationRepository.findMatchCandidates(user.getId(), List.of(egg.getId(), onion.getId()), 10))
                .extracting(MatchCandidate::getRecipeId)
                .containsExactlyInAnyOrder(liked.getId(), bookmarked.getId());
    }

    @Test
    @DisplayName("후보 수 제한만큼만 일치율 상위에서 가져온다")
    void limit() {
        Recipe full = recipe("달걀양파볶음", RecipeSource.COMMUNITY, egg, onion);
        recipe("토마토달걀볶음", RecipeSource.COMMUNITY, egg, tomato);
        em.flush();

        assertThat(recommendationRepository.findMatchCandidates(user.getId(), List.of(egg.getId(), onion.getId()), 1))
                .extracting(MatchCandidate::getRecipeId).containsExactly(full.getId());
    }

    private Ingredient ingredient(String name, boolean pantryStaple) {
        return ingredientRepository.save(Ingredient.builder()
                .name(name).category(IngredientCategory.ETC).baseUnit("g").pantryStaple(pantryStaple).build());
    }

    private Recipe recipe(String title, RecipeSource source, Ingredient... ingredients) {
        return recipeRepository.save(Recipe.builder()
                .author(user).source(source).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(Arrays.stream(ingredients).map(i -> new RecipeIngredient(i, null, null)).toList())
                .build());
    }
}
