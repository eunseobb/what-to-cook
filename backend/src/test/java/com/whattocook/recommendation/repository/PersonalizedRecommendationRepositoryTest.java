package com.whattocook.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.whattocook.community.domain.RecipeLike;
import com.whattocook.community.repository.RecipeLikeRepository;
import com.whattocook.global.config.JpaConfig;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recipe.repository.TagRepository;
import com.whattocook.recommendation.repository.PersonalizedRecommendationRepository.TasteCandidate;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.ExclusionType;
import com.whattocook.user.domain.User;
import com.whattocook.user.domain.UserExcludedIngredient;
import com.whattocook.user.domain.UserPreferenceTag;
import com.whattocook.user.repository.UserExcludedIngredientRepository;
import com.whattocook.user.repository.UserPreferenceTagRepository;
import com.whattocook.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
class PersonalizedRecommendationRepositoryTest {

    @Autowired
    private PersonalizedRecommendationRepository repository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserPreferenceTagRepository preferenceTagRepository;
    @Autowired
    private UserExcludedIngredientRepository excludedIngredientRepository;
    @Autowired
    private RecipeLikeRepository recipeLikeRepository;
    @Autowired
    private EntityManager em;

    private User user;
    private User other;
    private Ingredient egg;
    private Ingredient peanut;
    private Tag korean;
    private Tag spicy;
    private Tag western;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("taste@example.com", "hashed", "취향"));
        other = userRepository.save(new User("taste-other@example.com", "hashed", "남"));
        egg = ingredient("달걀");
        peanut = ingredient("땅콩");
        korean = tagRepository.save(new Tag(TagType.CUISINE, "테스트한식"));
        spicy = tagRepository.save(new Tag(TagType.TASTE, "테스트매운맛"));
        western = tagRepository.save(new Tag(TagType.CUISINE, "테스트양식"));
        preferenceTagRepository.save(new UserPreferenceTag(user, korean, 1.0));
        preferenceTagRepository.save(new UserPreferenceTag(user, spicy, 0.5));
        preferenceTagRepository.save(new UserPreferenceTag(other, western, 1.0));
        excludedIngredientRepository.save(new UserExcludedIngredient(user, peanut, ExclusionType.ALLERGY));
    }

    @Test
    @DisplayName("선호 태그 가중치 합, 좋아요 수 순으로 추리고, 알레르기·삭제·반응 없는 AI 레시피는 뺀다")
    void findTasteCandidates() {
        Recipe both = recipe("매운 한식", RecipeSource.COMMUNITY, List.of(korean, spicy), egg);
        Recipe koreanOnly = recipe("한식", RecipeSource.COMMUNITY, List.of(korean), egg);
        Recipe westernOnly = recipe("양식", RecipeSource.COMMUNITY, List.of(western), egg);
        Recipe untagged = recipe("태그 없음", RecipeSource.COMMUNITY, List.of(), egg);
        recipe("땅콩 한식", RecipeSource.COMMUNITY, List.of(korean, spicy), peanut);
        recipe("반응 없는 AI", RecipeSource.AI_GENERATED, List.of(korean), egg);
        Recipe likedAi = recipe("좋아요 받은 AI", RecipeSource.AI_GENERATED, List.of(), egg);
        recipe("삭제된 한식", RecipeSource.COMMUNITY, List.of(korean), egg).delete();
        recipeLikeRepository.save(new RecipeLike(other, likedAi));
        em.flush();
        em.createNativeQuery("update recipe set like_count = 5 where id = :id")
                .setParameter("id", westernOnly.getId()).executeUpdate();

        List<TasteCandidate> candidates = repository.findTasteCandidates(user.getId(), 10);

        // 다른 사용자의 취향(양식)은 점수에 안 들어가고, 취향 점수가 같으면 좋아요 수 → 최신 순이다.
        assertThat(candidates)
                .extracting(TasteCandidate::getRecipeId, TasteCandidate::getTasteScore)
                .containsExactly(tuple(both.getId(), 1.5), tuple(koreanOnly.getId(), 1.0),
                        tuple(westernOnly.getId(), 0.0), tuple(likedAi.getId(), 0.0), tuple(untagged.getId(), 0.0));
    }

    @Test
    @DisplayName("후보 수 제한만큼만 가져온다")
    void limit() {
        Recipe both = recipe("매운 한식", RecipeSource.COMMUNITY, List.of(korean, spicy), egg);
        recipe("한식", RecipeSource.COMMUNITY, List.of(korean), egg);
        em.flush();

        assertThat(repository.findTasteCandidates(user.getId(), 1))
                .extracting(TasteCandidate::getRecipeId).containsExactly(both.getId());
    }

    private Ingredient ingredient(String name) {
        return ingredientRepository.save(Ingredient.builder()
                .name(name).category(IngredientCategory.ETC).baseUnit("g").build());
    }

    private Recipe recipe(String title, RecipeSource source, List<Tag> tags, Ingredient ingredient) {
        return recipeRepository.save(Recipe.builder()
                .author(user).source(source).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1).tags(tags)
                .ingredients(List.of(new RecipeIngredient(ingredient, null, null)))
                .build());
    }
}
