package com.whattocook.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.global.config.JpaConfig;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.ExclusionType;
import com.whattocook.user.domain.User;
import com.whattocook.user.domain.UserExcludedIngredient;
import com.whattocook.user.repository.UserExcludedIngredientRepository;
import com.whattocook.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
class AiRecipeListRepositoryTest {

    @Autowired
    private AiRecipeListRepository aiRecipeListRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserExcludedIngredientRepository excludedIngredientRepository;
    @Autowired
    private EntityManager em;

    private User user;
    private Ingredient egg;
    private Ingredient peanut;
    private Ingredient tomato;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("ai-list@example.com", "hashed", "에이아이"));
        egg = ingredient("달걀");
        peanut = ingredient("땅콩");
        tomato = ingredient("토마토");
        excludedIngredientRepository.save(new UserExcludedIngredient(user, peanut, ExclusionType.ALLERGY));
        excludedIngredientRepository.save(new UserExcludedIngredient(user, tomato, ExclusionType.DISLIKE));
    }

    @Test
    @DisplayName("게시 중인 AI 레시피만 최신순으로 주고, 로그인 사용자의 알레르기 재료 레시피는 뺀다(비선호는 유지)")
    void findAiRecipes() {
        Recipe old = recipe("AI 달걀찜", RecipeSource.AI_GENERATED, egg);
        Recipe peanutRecipe = recipe("AI 땅콩조림", RecipeSource.AI_GENERATED, egg, peanut);
        Recipe tomatoRecipe = recipe("AI 토마토달걀", RecipeSource.AI_GENERATED, egg, tomato);
        recipe("커뮤니티 달걀말이", RecipeSource.COMMUNITY, egg);
        recipe("삭제된 AI", RecipeSource.AI_GENERATED, egg).delete();
        em.flush();

        assertThat(aiRecipeListRepository.findAiRecipes(user.getId(), Long.MAX_VALUE, Limit.of(10)))
                .extracting(Recipe::getId).containsExactly(tomatoRecipe.getId(), old.getId());
        assertThat(aiRecipeListRepository.findAiRecipes(null, Long.MAX_VALUE, Limit.of(10)))
                .extracting(Recipe::getId).containsExactly(tomatoRecipe.getId(), peanutRecipe.getId(), old.getId());
    }

    @Test
    @DisplayName("커서보다 작은 id부터 limit만큼 준다")
    void cursor() {
        Recipe first = recipe("AI 1", RecipeSource.AI_GENERATED, egg);
        Recipe second = recipe("AI 2", RecipeSource.AI_GENERATED, egg);
        recipe("AI 3", RecipeSource.AI_GENERATED, egg);
        em.flush();

        assertThat(aiRecipeListRepository.findAiRecipes(null, second.getId() + 1, Limit.of(1)))
                .extracting(Recipe::getId).containsExactly(second.getId());
        assertThat(aiRecipeListRepository.findAiRecipes(null, second.getId(), Limit.of(10)))
                .extracting(Recipe::getId).containsExactly(first.getId());
    }

    private Ingredient ingredient(String name) {
        return ingredientRepository.save(Ingredient.builder()
                .name(name).category(IngredientCategory.ETC).baseUnit("g").build());
    }

    private Recipe recipe(String title, RecipeSource source, Ingredient... ingredients) {
        return recipeRepository.save(Recipe.builder()
                .source(source).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(Arrays.stream(ingredients).map(i -> new RecipeIngredient(i, null, null)).toList())
                .build());
    }
}
