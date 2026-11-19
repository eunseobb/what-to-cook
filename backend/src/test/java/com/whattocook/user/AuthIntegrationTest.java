package com.whattocook.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whattocook.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 회원가입 → 로그인 → 보호 API 접근 → 재발급(rotation) → 로그아웃 흐름을 실제 PostgreSQL·Redis로 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("가입부터 로그아웃까지 전체 흐름이 동작하고, 쓴 Refresh 토큰은 재사용할 수 없다")
    void fullFlow() throws Exception {
        postJson("/api/auth/signup", """
                {"email":"flow@example.com","password":"password123","nickname":"요리사"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber());

        postJson("/api/auth/signup", """
                {"email":"FLOW@example.com","password":"password123","nickname":"요리사"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U001"));

        postJson("/api/auth/login", """
                {"email":"flow@example.com","password":"wrong-password"}""")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A003"));

        JsonNode tokens = read(postJson("/api/auth/login", """
                {"email":"flow@example.com","password":"password123"}""")
                .andExpect(status().isOk()));

        // 유효한 Access 토큰이면 인증을 통과한다 (해당 경로에 API가 없어 404).
        mockMvc.perform(get("/api/anything")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/anything").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));

        String oldRefresh = tokens.get("refreshToken").asText();
        JsonNode rotated = read(postJson("/api/auth/refresh", refreshBody(oldRefresh))
                .andExpect(status().isOk()));
        String newRefresh = rotated.get("refreshToken").asText();

        postJson("/api/auth/refresh", refreshBody(oldRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A004"));

        postJson("/api/auth/logout", refreshBody(newRefresh))
                .andExpect(status().isNoContent());
        postJson("/api/auth/refresh", refreshBody(newRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원가입 입력값이 잘못되면 400과 필드 오류를 응답한다")
    void signupValidation() throws Exception {
        postJson("/api/auth/signup", """
                {"email":"not-an-email","password":"short","nickname":""}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }
}
