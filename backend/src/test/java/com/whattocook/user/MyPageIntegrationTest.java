package com.whattocook.user;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whattocook.community.domain.RecipeBookmark;
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
 * 마이페이지 두 목록(내 레시피, 저장한 레시피)을 실제 DB로 조회한다. 로그인 필요, 숨김 레시피 제외, 커서 이어 받기를 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MyPageIntegrationTest {

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
    private Recipe first;
    private Recipe hidden;
    private Recipe latest;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("mypage-user@example.com", "hashed", "마이"));
        first = saveRecipe("첫째");
        hidden = saveRecipe("숨김");
        latest = saveRecipe("최신");
        for (Recipe recipe : List.of(first, hidden, latest)) {
            recipeBookmarkRepository.save(new RecipeBookmark(user, recipe));
        }
        hidden.hide();
        hidden = recipeRepository.save(hidden);
    }

    @AfterEach
    void tearDown() {
        // 다른 통합 테스트와 같은 DB를 쓰므로 만든 데이터만 FK 순서대로 지운다.
        recipeBookmarkRepository.deleteAll();
        recipeRepository.deleteAll(List.of(first, hidden, latest));
        userRepository.delete(user);
    }

    @Test
    @DisplayName("두 목록 모두 로그인이 필요하고, 숨김 레시피를 빼고 최신순으로 커서를 이어 받는다")
    void lists() throws Exception {
        for (String path : List.of("/api/me/recipes", "/api/me/bookmarks")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());

            String nextCursor = mockMvc.perform(get(path).param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", contains(latest.getId().intValue())))
                    .andExpect(jsonPath("$.items[0].authorNickname").value("마이"))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString()
                    .replaceAll(".*\"nextCursor\":\"(\\d+)\".*", "$1");

            mockMvc.perform(get(path).param("size", "1").param("cursor", nextCursor)
                            .header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", contains(first.getId().intValue())))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    private Recipe saveRecipe(String title) {
        return recipeRepository.save(Recipe.builder()
                .author(user).source(RecipeSource.COMMUNITY).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(List.of()).steps(List.of())
                .build());
    }
}
