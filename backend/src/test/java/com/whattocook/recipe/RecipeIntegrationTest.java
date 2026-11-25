package com.whattocook.recipe;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whattocook.global.security.JwtTokenProvider;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recipe.repository.TagRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.ExclusionType;
import com.whattocook.user.domain.User;
import com.whattocook.user.domain.UserExcludedIngredient;
import com.whattocook.user.repository.UserExcludedIngredientRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 레시피 등록 → 목록·상세(비로그인) → 작성자 수정 / 타인 수정 거부 → 삭제 흐름을 실제 DB로 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecipeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    private UserExcludedIngredientRepository excludedIngredientRepository;

    private User author;
    private User other;
    private Ingredient egg;
    private Ingredient greenOnion;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User("recipe-author@example.com", "hashed", "작성자"));
        other = userRepository.save(new User("recipe-other@example.com", "hashed", "다른사람"));
        egg = ingredientRepository.save(Ingredient.builder()
                .name("달걀").category(IngredientCategory.DAIRY_EGG).baseUnit("개").build());
        greenOnion = ingredientRepository.save(Ingredient.builder()
                .name("대파").category(IngredientCategory.VEGETABLE).baseUnit("g").build());
    }

    @AfterEach
    void tearDown() {
        // 다른 통합 테스트와 같은 DB를 쓰므로 만든 데이터만 FK 순서대로 지운다.
        recipeRepository.deleteAll();
        // 알레르기 설정은 사용자 삭제 때 DB에서 같이 지워지므로 재료보다 먼저 지운다.
        userRepository.deleteAll(List.of(author, other));
        ingredientRepository.deleteAll(List.of(egg, greenOnion));
    }

    @Test
    @DisplayName("작성자는 등록·수정·삭제하고, 누구나 목록·상세를 조회하며, 다른 사용자는 수정할 수 없다")
    void crudFlow() throws Exception {
        Long tagId = tagRepository.findAll().get(0).getId();
        String body = """
                {"title":"파달걀볶음","mainImageUrl":"https://img.example.com/main.jpg",
                 "difficulty":"EASY","cookTimeMinutes":10,"servings":1,
                 "ingredients":[{"ingredientId":%d,"quantity":2,"unit":"개"},{"ingredientId":%d}],
                 "steps":[{"content":"파를 썬다"},{"content":"달걀과 볶는다","imageUrl":"https://img.example.com/2.jpg"}],
                 "tagIds":[%d]}""".formatted(egg.getId(), greenOnion.getId(), tagId);

        mockMvc.perform(json(post("/api/recipes"), body)).andExpect(status().isUnauthorized());

        String created = mockMvc.perform(json(post("/api/recipes"), body).header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long recipeId = objectMapper.readTree(created).get("recipeId").asLong();

        mockMvc.perform(get("/api/recipes").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(recipeId))
                .andExpect(jsonPath("$.items[0].authorNickname").value("작성자"))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(get("/api/recipes/{id}", recipeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("COMMUNITY"))
                .andExpect(jsonPath("$.ingredients[0].name").value("달걀"))
                .andExpect(jsonPath("$.ingredients[0].quantity").value(2))
                .andExpect(jsonPath("$.ingredients[1].name").value("대파"))
                .andExpect(jsonPath("$.steps[1].order").value(2))
                .andExpect(jsonPath("$.steps[1].imageUrl").value("https://img.example.com/2.jpg"))
                .andExpect(jsonPath("$.tags[0].id").value(tagId));

        mockMvc.perform(json(patch("/api/recipes/{id}", recipeId), "{\"title\":\"남의 레시피\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("R002"));

        mockMvc.perform(json(patch("/api/recipes/{id}", recipeId),
                        "{\"title\":\"대파달걀볶음\",\"steps\":[{\"content\":\"한 번에 볶는다\"}]}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/recipes/{id}", recipeId))
                .andExpect(jsonPath("$.title").value("대파달걀볶음"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.ingredients.length()").value(2));

        mockMvc.perform(delete("/api/recipes/{id}", recipeId).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/recipes/{id}", recipeId).header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/recipes/{id}", recipeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("입력값이 잘못되면 공통 에러 포맷으로 400을 응답한다")
    void validation() throws Exception {
        mockMvc.perform(json(post("/api/recipes"), """
                        {"title":" ","mainImageUrl":"javascript:alert(1)","difficulty":"EASY",
                         "cookTimeMinutes":0,"servings":1,"ingredients":[],"steps":[{"content":"x"}]}""")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors.length()").value(4));

        mockMvc.perform(json(post("/api/recipes"), """
                        {"title":"없는 재료","difficulty":"EASY","cookTimeMinutes":5,"servings":1,
                         "ingredients":[{"ingredientId":-1}],"steps":[{"content":"x"}]}""")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R003"));

        mockMvc.perform(get("/api/recipes").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("태그·기간·인기순으로 목록을 거르고, 복합 커서로 다음 페이지를 이어 받으며, 숨김 레시피는 뺀다")
    void listWithFilterAndPopularSort() throws Exception {
        Tag tag = tagRepository.findAll().get(0);
        Recipe less = recipeRepository.save(listed("덜 인기", 2, tag));
        Recipe more = recipeRepository.save(listed("더 인기", 5, tag));
        recipeRepository.save(listed("태그 없음", 9, null));
        Recipe hidden = listed("숨김", 100, tag);
        hidden.hide();
        recipeRepository.save(hidden);

        String first = mockMvc.perform(get("/api/recipes").param("tag", tag.getId().toString())
                        .param("sort", "POPULAR").param("days", "7").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(more.getId()))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = objectMapper.readTree(first).get("nextCursor").asText();

        mockMvc.perform(get("/api/recipes").param("tag", tag.getId().toString())
                        .param("sort", "POPULAR").param("days", "7").param("size", "1").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(less.getId()))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(get("/api/recipes").param("sort", "POPULAR").param("cursor", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(get("/api/recipes").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(get("/api/recipes").param("sort", "RANDOM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("출처로 거르고, 로그인 사용자는 알레르기 재료가 든 레시피를 받지 않는다")
    void listBySourceExcludingAllergy() throws Exception {
        Recipe eggAi = recipeRepository.save(aiRecipe("AI 달걀찜", egg));
        Recipe onionAi = recipeRepository.save(aiRecipe("AI 파무침", greenOnion));
        recipeRepository.save(listed("커뮤니티", 0, null));
        excludedIngredientRepository.save(new UserExcludedIngredient(other, greenOnion, ExclusionType.ALLERGY));

        mockMvc.perform(get("/api/recipes").param("source", "AI_GENERATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(onionAi.getId().intValue(), eggAi.getId().intValue())));
        mockMvc.perform(get("/api/recipes").param("source", "AI_GENERATED").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains(eggAi.getId().intValue())));
        mockMvc.perform(get("/api/recipes").param("source", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    private Recipe aiRecipe(String title, Ingredient ingredient) {
        return Recipe.builder().source(RecipeSource.AI_GENERATED).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(List.of(new RecipeIngredient(ingredient, null, null))).build();
    }

    private Recipe listed(String title, int likeCount, Tag tag) {
        Recipe recipe = Recipe.builder().author(author).source(RecipeSource.COMMUNITY).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .tags(tag == null ? List.of() : List.of(tag)).build();
        for (int i = 0; i < likeCount; i++) {
            recipe.increaseLikeCount();
        }
        return recipe;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }
}
