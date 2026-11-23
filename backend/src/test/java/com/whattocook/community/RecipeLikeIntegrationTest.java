package com.whattocook.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whattocook.community.service.RedisRecipeLikeService;
import com.whattocook.global.security.JwtTokenProvider;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
import org.junit.jupiter.api.AfterEach;
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
 * 기본(Redis 원자 연산) 방식의 좋아요 API 흐름과 동시성을 실제 DB·Redis로 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecipeLikeIntegrationTest extends RecipeLikeConcurrencyTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private RedisRecipeLikeService redisRecipeLikeService;

    @Override
    protected void syncLikeCount() {
        redisRecipeLikeService.flushLikeCounts();
    }

    @AfterEach
    void flushRemaining() {
        // 남은 증감분을 비워 다음 테스트에 섞이지 않게 한다.
        syncLikeCount();
    }

    @Test
    @DisplayName("좋아요 → 중복 거부 → 반영 후 카운트 노출 → 취소 → 삭제된 레시피 거부")
    void likeFlow() throws Exception {
        User liker = users.get(1);
        Long recipeId = recipe.getId();

        mockMvc.perform(post("/api/recipes/{id}/likes", recipeId)).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/recipes/{id}/likes", recipeId).header(HttpHeaders.AUTHORIZATION, bearer(liker)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/recipes/{id}/likes", recipeId).header(HttpHeaders.AUTHORIZATION, bearer(liker)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("L001"));

        syncLikeCount();
        mockMvc.perform(get("/api/recipes/{id}", recipeId)).andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(delete("/api/recipes/{id}/likes", recipeId).header(HttpHeaders.AUTHORIZATION, bearer(liker)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/recipes/{id}/likes", recipeId).header(HttpHeaders.AUTHORIZATION, bearer(liker)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("L002"));

        syncLikeCount();
        mockMvc.perform(get("/api/recipes/{id}", recipeId)).andExpect(jsonPath("$.likeCount").value(0));

        mockMvc.perform(delete("/api/recipes/{id}", recipeId).header(HttpHeaders.AUTHORIZATION, bearer(users.get(0))))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/recipes/{id}/likes", recipeId).header(HttpHeaders.AUTHORIZATION, bearer(liker)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }
}
