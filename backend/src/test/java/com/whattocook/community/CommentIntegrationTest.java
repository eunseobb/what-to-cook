package com.whattocook.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whattocook.community.repository.CommentRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 댓글 등록 → 대댓글 → 목록(비로그인) → 작성자 수정 / 타인 거부 → 소프트 삭제 흐름을 실제 DB로 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CommentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private CommentRepository commentRepository;

    private User writer;
    private User other;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        writer = userRepository.save(new User("comment-writer@example.com", "hashed", "작성자"));
        other = userRepository.save(new User("comment-other@example.com", "hashed", "다른사람"));
        recipe = recipeRepository.save(Recipe.builder()
                .author(writer).source(RecipeSource.COMMUNITY).title("파달걀볶음")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .build());
    }

    @AfterEach
    void tearDown() {
        // 대댓글이 부모를 참조하므로 한 문장으로 지운다.
        commentRepository.deleteAllInBatch();
        recipeRepository.delete(recipe);
        userRepository.deleteAll(List.of(writer, other));
    }

    @Test
    @DisplayName("댓글·대댓글을 달고, 누구나 목록을 보며, 작성자만 수정·삭제하고, 삭제된 부모는 대댓글과 함께 남는다")
    void commentFlow() throws Exception {
        String path = "/api/recipes/" + recipe.getId() + "/comments";
        mockMvc.perform(json(post(path), "{\"content\":\"맛있어요\"}")).andExpect(status().isUnauthorized());

        long rootId = create(path, writer, "{\"content\":\"맛있어요\"}");
        long replyId = create(path, other, "{\"content\":\"저도요\",\"parentId\":%d}".formatted(rootId));
        long secondId = create(path, other, "{\"content\":\"두 번째\"}");

        mockMvc.perform(json(post(path), "{\"content\":\"답글의 답글\",\"parentId\":%d}".formatted(replyId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(writer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CM003"));

        mockMvc.perform(get(path).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(rootId))
                .andExpect(jsonPath("$.items[0].authorNickname").value("작성자"))
                .andExpect(jsonPath("$.items[0].replies[0].id").value(replyId))
                .andExpect(jsonPath("$.items[0].replies[0].content").value("저도요"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(String.valueOf(rootId)));
        mockMvc.perform(get(path).param("cursor", String.valueOf(rootId)))
                .andExpect(jsonPath("$.items[0].id").value(secondId))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(json(patch("/api/comments/{id}", rootId), "{\"content\":\"남의 댓글\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CM002"));
        mockMvc.perform(json(patch("/api/comments/{id}", rootId), "{\"content\":\"정말 맛있어요\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(writer)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/comments/{id}", rootId).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/comments/{id}", rootId).header(HttpHeaders.AUTHORIZATION, bearer(writer)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/comments/{id}", secondId).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(path))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(rootId))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].content").value("삭제된 댓글"))
                .andExpect(jsonPath("$.items[0].authorNickname").doesNotExist())
                .andExpect(jsonPath("$.items[0].replies[0].content").value("저도요"));

        mockMvc.perform(json(patch("/api/comments/{id}", rootId), "{\"content\":\"되살리기\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(writer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CM001"));
    }

    @Test
    @DisplayName("입력값이 잘못되거나 레시피가 없으면 공통 에러 포맷으로 응답한다")
    void validation() throws Exception {
        mockMvc.perform(json(post("/api/recipes/{id}/comments", recipe.getId()), "{\"content\":\" \"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(writer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        mockMvc.perform(get("/api/recipes/{id}/comments", -1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    private long create(String path, User user, String body) throws Exception {
        String response = mockMvc.perform(json(post(path), body).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("commentId").asLong();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }
}
