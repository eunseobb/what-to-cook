package com.whattocook.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.whattocook.ai.client.AiRecipeClient;
import com.whattocook.ai.client.AiRecipeDraft;
import com.whattocook.ai.service.IngredientSetHash;
import com.whattocook.global.security.JwtTokenProvider;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import java.math.BigDecimal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiRecommendationIntegrationTest {

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
    /** 실제 OpenAI를 부르지 않는다. */
    @MockitoBean
    private AiRecipeClient aiRecipeClient;

    private User user;
    private User other;
    private Ingredient egg;
    private Ingredient onion;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("ai-it@example.com", "hashed", "에이아이"));
        other = userRepository.save(new User("ai-other@example.com", "hashed", "다른사람"));
        egg = ingredientRepository.save(Ingredient.builder()
                .name("달걀").category(IngredientCategory.ETC).baseUnit("개").aliases(List.of("계란")).build());
        onion = ingredientRepository.save(Ingredient.builder()
                .name("양파").category(IngredientCategory.ETC).baseUnit("g").build());
    }

    @AfterEach
    void tearDown() {
        // 다른 통합 테스트와 같은 DB를 쓰므로 만든 데이터만 FK 순서대로 지운다.
        recipeRepository.deleteAll(recipeRepository.findAllByIngredientSetHashAndSourceAndStatusOrderByIdAsc(
                IngredientSetHash.of(List.of(egg.getId(), onion.getId())), RecipeSource.AI_GENERATED, RecipeStatus.PUBLISHED));
        userRepository.deleteAll(List.of(user, other));
        ingredientRepository.deleteAll(List.of(egg, onion));
    }

    @Test
    @DisplayName("처음엔 생성 작업을 폴링해 결과를 받고, 같은 재료 집합으로 다시 요청하면 LLM 없이 재사용한다")
    void generateThenReuse() throws Exception {
        given(aiRecipeClient.generate(anyList())).willReturn(List.of(new AiRecipeDraft("달걀양파볶음", Difficulty.EASY, 10, 1,
                List.of(new AiRecipeDraft.Item("달걀", new BigDecimal("2"), "개"),
                        new AiRecipeDraft.Item("양파", new BigDecimal("100"), "g")),
                List.of("양파를 썬다", "달걀과 볶는다"))));

        String body = mockMvc.perform(request(user, "{\"ingredients\":[\"계란\",\"양파\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(body, "$.jobId");

        String status = "PENDING";
        for (int i = 0; i < 50 && status.equals("PENDING"); i++) {
            Thread.sleep(100);
            status = JsonPath.read(mockMvc.perform(poll(user, jobId)).andReturn().getResponse().getContentAsString(),
                    "$.status");
        }
        assertThat(status).isEqualTo("COMPLETED");
        mockMvc.perform(poll(user, jobId))
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.recipes[0].title").value("달걀양파볶음"))
                .andExpect(jsonPath("$.recipes[0].source").value("AI_GENERATED"))
                .andExpect(jsonPath("$.recipes[0].ingredients[0].name").value("달걀"));
        mockMvc.perform(poll(other, jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI002"));

        // 입력 순서·별칭이 달라도 같은 재료 집합이면 재사용한다.
        mockMvc.perform(request(user, "{\"ingredients\":[\"양파\",\"달걀\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recipes[0].title").value("달걀양파볶음"));
        verify(aiRecipeClient, times(1)).generate(anyList());
    }

    @Test
    @DisplayName("로그인이 필요하고, 재료가 없거나 매칭되지 않으면 거부한다")
    void validation() throws Exception {
        mockMvc.perform(post("/api/recommendations/ai").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\":[\"양파\"]}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(user, "{\"ingredients\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        mockMvc.perform(request(user, "{\"ingredients\":[\"없는재료\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI001"));
        mockMvc.perform(poll(user, "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder request(User requester, String body) {
        return post("/api/recommendations/ai")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(requester.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder poll(User requester, String jobId) {
        return get("/api/recommendations/ai/" + jobId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(requester.getId()));
    }
}
