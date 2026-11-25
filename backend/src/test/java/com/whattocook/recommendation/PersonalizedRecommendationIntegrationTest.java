package com.whattocook.recommendation;

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
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recipe.repository.TagRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.ExclusionType;
import com.whattocook.user.domain.User;
import com.whattocook.user.domain.UserExcludedIngredient;
import com.whattocook.user.domain.UserPreferenceTag;
import com.whattocook.user.repository.UserExcludedIngredientRepository;
import com.whattocook.user.repository.UserPreferenceTagRepository;
import com.whattocook.user.repository.UserRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PersonalizedRecommendationIntegrationTest {

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
    private TagRepository tagRepository;
    @Autowired
    private UserPreferenceTagRepository preferenceTagRepository;
    @Autowired
    private UserExcludedIngredientRepository excludedIngredientRepository;

    private User user;
    private Ingredient egg;
    private Ingredient peanut;
    private Tag favorite;
    private List<Recipe> recipes;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("personalized-it@example.com", "hashed", "취향"));
        egg = ingredient("취향테스트달걀");
        peanut = ingredient("취향테스트땅콩");
        favorite = tagRepository.save(new Tag(TagType.CUISINE, "취향테스트태그"));
        preferenceTagRepository.save(new UserPreferenceTag(user, favorite, 1.0));
        excludedIngredientRepository.save(new UserExcludedIngredient(user, peanut, ExclusionType.ALLERGY));
        recipes = List.of(recipe("취향 없는 달걀찜", List.of(), egg), recipe("취향 달걀말이", List.of(favorite), egg),
                recipe("취향 땅콩조림", List.of(favorite), peanut));
    }

    @AfterEach
    void tearDown() {
        // 다른 통합 테스트와 같은 DB를 쓰므로 만든 데이터만 FK 순서대로 지운다. 선호 태그·제외 재료는 사용자 CASCADE로 지워진다.
        recipeRepository.deleteAll(recipes);
        userRepository.deleteAll(List.of(user));
        tagRepository.deleteAll(List.of(favorite));
        ingredientRepository.deleteAll(List.of(egg, peanut));
    }

    @Test
    @DisplayName("재료 입력 없이 선호 태그 레시피를 먼저 주고, 알레르기 재료가 든 레시피는 뺀다")
    void recommend() throws Exception {
        mockMvc.perform(request(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].recipe.title").value("취향 달걀말이"))
                .andExpect(jsonPath("$.items[?(@.recipe.title == '취향 없는 달걀찜')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.recipe.title == '취향 땅콩조림')]").isEmpty());
    }

    @Test
    @DisplayName("로그인이 필요하고, 커서 형식이나 페이지 크기가 틀리면 거부한다")
    void validation() throws Exception {
        mockMvc.perform(get("/api/recommendations/personalized"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request("?cursor=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(request("?size=51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    private MockHttpServletRequestBuilder request(String query) {
        return get("/api/recommendations/personalized" + query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(user.getId()));
    }

    private Ingredient ingredient(String name) {
        return ingredientRepository.save(Ingredient.builder()
                .name(name).category(IngredientCategory.ETC).baseUnit("g").build());
    }

    private Recipe recipe(String title, List<Tag> tags, Ingredient ingredient) {
        return recipeRepository.save(Recipe.builder()
                .author(user).source(RecipeSource.COMMUNITY).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1).tags(tags)
                .ingredients(List.of(new RecipeIngredient(ingredient, null, null)))
                .build());
    }
}
