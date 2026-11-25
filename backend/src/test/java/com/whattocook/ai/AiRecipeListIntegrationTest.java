package com.whattocook.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whattocook.global.security.JwtTokenProvider;
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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiRecipeListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private UserExcludedIngredientRepository excludedIngredientRepository;

    private User user;
    private Ingredient egg;
    private Ingredient peanut;
    private List<Recipe> recipes;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("ai-list-it@example.com", "hashed", "에이아이목록"));
        egg = ingredient("달걀");
        peanut = ingredient("땅콩");
        excludedIngredientRepository.save(new UserExcludedIngredient(user, peanut, ExclusionType.ALLERGY));
        recipes = List.of(recipe("AI 달걀찜", RecipeSource.AI_GENERATED, egg),
                recipe("AI 땅콩조림", RecipeSource.AI_GENERATED, egg, peanut),
                recipe("커뮤니티 달걀말이", RecipeSource.COMMUNITY, egg));
    }

    @AfterEach
    void tearDown() {
        // 다른 통합 테스트와 같은 DB를 쓰므로 만든 데이터만 FK 순서대로 지운다.
        recipeRepository.deleteAll(recipes);
        userRepository.deleteAll(List.of(user));
        ingredientRepository.deleteAll(List.of(egg, peanut));
    }

    @Test
    @DisplayName("비로그인은 AI 레시피를 최신순으로 받고, 커뮤니티 레시피는 섞이지 않는다")
    void anonymous() throws Exception {
        // 같은 DB의 다른 테스트 데이터와 섞이지 않게 커뮤니티 레시피 id 바로 앞에서 시작한다.
        mockMvc.perform(get("/api/recipes/ai").param("cursor", String.valueOf(recipes.get(2).getId() + 1))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("AI 땅콩조림"))
                .andExpect(jsonPath("$.items[0].source").value("AI_GENERATED"))
                .andExpect(jsonPath("$.items[1].title").value("AI 달걀찜"));
    }

    @Test
    @DisplayName("로그인 사용자는 알레르기 재료가 든 AI 레시피를 받지 않는다")
    void excludesAllergy() throws Exception {
        mockMvc.perform(get("/api/recipes/ai").param("cursor", String.valueOf(recipes.get(2).getId() + 1))
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("AI 달걀찜"));
    }

    @Test
    @DisplayName("size 범위를 벗어나면 거부한다")
    void validation() throws Exception {
        mockMvc.perform(get("/api/recipes/ai").param("size", "51"))
                .andExpect(status().isBadRequest());
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
