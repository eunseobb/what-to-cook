package com.whattocook.recommendation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecommendationIntegrationTest {

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
    @Autowired
    private TagRepository tagRepository;

    private User user;
    private Ingredient egg;
    private Ingredient onion;
    private Ingredient peanut;
    private List<Recipe> recipes;
    private List<Tag> tags = List.of();

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("recommend-it@example.com", "hashed", "추천"));
        egg = ingredient("달걀", "계란");
        onion = ingredient("양파");
        peanut = ingredient("땅콩");
        excludedIngredientRepository.save(new UserExcludedIngredient(user, peanut, ExclusionType.ALLERGY));
        recipes = List.of(recipe("달걀양파볶음", egg, onion), recipe("땅콩달걀조림", egg, peanut),
                recipe("양파수프", onion, egg, peanut));
    }

    @AfterEach
    void tearDown() {
        // 다른 통합 테스트와 같은 DB를 쓰므로 만든 데이터만 FK 순서대로 지운다. 제외 재료는 사용자 CASCADE로 지워진다.
        recipeRepository.deleteAll(recipes);
        tagRepository.deleteAll(tags);
        userRepository.deleteAll(List.of(user));
        ingredientRepository.deleteAll(List.of(egg, onion, peanut));
    }

    @Test
    @DisplayName("별칭 입력을 정규화해 추천하고, 알레르기 재료가 든 레시피는 뺀다")
    void recommend() throws Exception {
        mockMvc.perform(request("{\"ingredients\":[\"계란\",\"양파\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].recipe.title").value("달걀양파볶음"))
                .andExpect(jsonPath("$.items[0].matchRate").value(1.0))
                .andExpect(jsonPath("$.items[0].requiredIngredientCount").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("로그인이 필요하고, 재료가 없거나 커서 형식이 틀리면 거부한다")
    void validation() throws Exception {
        mockMvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\":[\"양파\"]}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request("{\"ingredients\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(request("{\"ingredients\":[\"양파\"],\"cursor\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("쿼리스트링 재료 ID·태그로 추천하고, 같은 요청은 같은 결과를 준다")
    void recommendByQuery() throws Exception {
        Tag korean = tagRepository.save(new Tag(TagType.CUISINE, "한식-추천IT"));
        tags = List.of(korean);
        Recipe tagged = recipe("달걀국", egg);
        tagged.replaceTags(List.of(korean));
        recipeRepository.save(tagged);
        recipes = List.of(recipes.get(0), recipes.get(1), recipes.get(2), tagged);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/recommendations")
                            .param("ingredient", egg.getId().toString(), onion.getId().toString())
                            .param("tag", korean.getId().toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].recipe.title").value("달걀국"))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
        mockMvc.perform(get("/api/recommendations")
                        .param("ingredient", egg.getId().toString(), onion.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("쿼리스트링 추천은 로그인이 필요하고, 재료가 없거나 ID 형식이 틀리면 거부한다")
    void queryValidation() throws Exception {
        mockMvc.perform(get("/api/recommendations").param("ingredient", "1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recommendations").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(get("/api/recommendations").param("ingredient", "abc")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    private MockHttpServletRequestBuilder request(String body) {
        return post("/api/recommendations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private Ingredient ingredient(String name, String... aliases) {
        return ingredientRepository.save(Ingredient.builder()
                .name(name).category(IngredientCategory.ETC).baseUnit("g").aliases(List.of(aliases)).build());
    }

    private Recipe recipe(String title, Ingredient... ingredients) {
        return recipeRepository.save(Recipe.builder()
                .author(user).source(RecipeSource.COMMUNITY).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(Arrays.stream(ingredients).map(i -> new RecipeIngredient(i, null, null)).toList())
                .build());
    }
}
