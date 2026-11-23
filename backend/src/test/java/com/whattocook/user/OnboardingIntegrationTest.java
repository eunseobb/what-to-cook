package com.whattocook.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whattocook.global.security.JwtTokenProvider;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;
import com.whattocook.recipe.repository.TagRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 태그 목록(비로그인) → 온보딩 저장 → 조회 → 다시 저장(전체 교체) 흐름을 실제 DB로 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OnboardingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private UserPreferenceTagRepository preferenceTagRepository;

    private User user;
    private Ingredient peanut;
    private Ingredient cucumber;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("onboarding@example.com", "hashed", "온보딩"));
        peanut = ingredientRepository.save(Ingredient.builder()
                .name("땅콩").category(IngredientCategory.ETC).baseUnit("g").build());
        cucumber = ingredientRepository.save(Ingredient.builder()
                .name("오이").category(IngredientCategory.VEGETABLE).baseUnit("개").build());
    }

    @AfterEach
    void tearDown() {
        // 다른 통합 테스트와 같은 DB를 쓴다. 사용자를 지우면 온보딩 행은 FK CASCADE로 함께 지워진다.
        userRepository.deleteAll(List.of(user));
        ingredientRepository.deleteAll(List.of(peanut, cucumber));
    }

    @Test
    @DisplayName("태그 목록은 비로그인으로 종류별 조회할 수 있다")
    void listTags() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(19));
        mockMvc.perform(get("/api/tags").param("type", "TASTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].type").value("TASTE"))
                .andExpect(jsonPath("$[0].name").value("매운맛"));
        mockMvc.perform(get("/api/tags").param("type", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("본인 온보딩 정보를 저장·조회하고, 다시 저장하면 통째로 교체된다")
    void replaceAndGet() throws Exception {
        Tag korean = tagRepository.findAllByTypeOrderByIdAsc(TagType.CUISINE).get(0);
        Tag spicy = tagRepository.findAllByTypeOrderByIdAsc(TagType.TASTE).get(0);

        mockMvc.perform(get("/api/users/me/onboarding")).andExpect(status().isUnauthorized());

        mockMvc.perform(json(put("/api/users/me/onboarding"), """
                        {"tagIds":[%d,%d],"allergyIngredientIds":[%d],"dislikeIngredientIds":[%d]}"""
                        .formatted(korean.getId(), spicy.getId(), peanut.getId(), cucumber.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/users/me/onboarding")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenceTags.length()").value(2))
                .andExpect(jsonPath("$.preferenceTags[0].name").value(korean.getName()))
                .andExpect(jsonPath("$.allergyIngredients[0].name").value("땅콩"))
                .andExpect(jsonPath("$.dislikeIngredients[0].name").value("오이"));
        assertThat(preferenceTagRepository.findAllByUserIdOrderByTagIdAsc(user.getId()))
                .allSatisfy(p -> assertThat(p.getWeight()).isEqualTo(1.0));

        mockMvc.perform(json(put("/api/users/me/onboarding"), """
                        {"tagIds":[%d],"dislikeIngredientIds":[%d]}""".formatted(spicy.getId(), peanut.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/users/me/onboarding")))
                .andExpect(jsonPath("$.preferenceTags.length()").value(1))
                .andExpect(jsonPath("$.preferenceTags[0].id").value(spicy.getId()))
                .andExpect(jsonPath("$.allergyIngredients.length()").value(0))
                .andExpect(jsonPath("$.dislikeIngredients[0].name").value("땅콩"));
    }

    @Test
    @DisplayName("없는 태그·재료나 중복 재료는 거부한다")
    void validation() throws Exception {
        mockMvc.perform(json(put("/api/users/me/onboarding"), "{\"tagIds\":[-1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("O001"));
        mockMvc.perform(json(put("/api/users/me/onboarding"), "{\"allergyIngredientIds\":[-1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("O002"));
        mockMvc.perform(json(put("/api/users/me/onboarding"), """
                        {"allergyIngredientIds":[%d],"dislikeIngredientIds":[%d]}"""
                        .formatted(peanut.getId(), peanut.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("O003"));
        mockMvc.perform(json(put("/api/users/me/onboarding"), "{\"tagIds\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return authed(builder).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(user.getId()));
    }
}
