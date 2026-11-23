package com.whattocook.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whattocook.community.repository.RecipeBookmarkRepository;
import com.whattocook.global.security.JwtTokenProvider;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
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
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 저장 → 중복 저장(멱등) → 해제 → 중복 해제(멱등), 삭제된 레시피 저장 거부를 실제 DB로 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecipeBookmarkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeBookmarkRepository recipeBookmarkRepository;

    private User user;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("bookmark-user@example.com", "hashed", "저장러"));
        recipe = recipeRepository.save(Recipe.builder()
                .author(user).source(RecipeSource.COMMUNITY).title("파달걀볶음")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(List.of()).steps(List.of())
                .build());
    }

    @AfterEach
    void tearDown() {
        // 다른 통합 테스트와 같은 DB를 쓰므로 만든 데이터만 FK 순서대로 지운다.
        recipeBookmarkRepository.deleteAll();
        recipeRepository.delete(recipe);
        userRepository.delete(user);
    }

    @Test
    @DisplayName("저장·해제는 멱등이고, 로그인이 필요하다")
    void bookmarkFlow() throws Exception {
        String path = "/api/recipes/{id}/bookmarks";

        mockMvc.perform(post(path, recipe.getId())).andExpect(status().isUnauthorized());

        mockMvc.perform(post(path, recipe.getId()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(path, recipe.getId()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertThat(recipeBookmarkRepository.count()).isEqualTo(1);

        mockMvc.perform(delete(path, recipe.getId()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(path, recipe.getId()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertThat(recipeBookmarkRepository.count()).isZero();
    }

    @Test
    @DisplayName("삭제된 레시피는 저장할 수 없다")
    void bookmarkDeletedRecipe() throws Exception {
        recipe.delete();
        recipe = recipeRepository.save(recipe);

        mockMvc.perform(post("/api/recipes/{id}/bookmarks", recipe.getId()).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
        assertThat(recipeBookmarkRepository.count()).isZero();
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }
}
