package com.whattocook.recipe;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whattocook.global.security.JwtTokenProvider;
import com.whattocook.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Presigned URL 발급 API. 서명만 하므로 실제 스토리지 없이 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UploadIntegrationTest {

    private static final String URL = "/api/uploads/presigned-url";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("로그인 사용자에게 업로드 URL과 최종 이미지 URL을 발급한다")
    void issue() throws Exception {
        mockMvc.perform(request("{\"fileName\":\"main.png\",\"contentType\":\"image/png\",\"fileSize\":2048}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(42L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl", startsWith("http://localhost:9000/test-bucket/recipes/42/")))
                .andExpect(jsonPath("$.imageUrl",
                        matchesPattern("http://localhost:9000/test-bucket/recipes/42/[0-9a-f-]{36}\\.png")))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    @DisplayName("비로그인 요청은 401이다")
    void unauthorized() throws Exception {
        mockMvc.perform(request("{\"fileName\":\"main.png\",\"contentType\":\"image/png\",\"fileSize\":2048}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이미지가 아니거나 너무 크거나 필수값이 없으면 400이다")
    void invalid() throws Exception {
        String bearer = "Bearer " + jwtTokenProvider.createAccessToken(42L);
        mockMvc.perform(request("{\"fileName\":\"x.gif\",\"contentType\":\"image/gif\",\"fileSize\":2048}")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UP001"));
        mockMvc.perform(request("{\"fileName\":\"x.exe\",\"contentType\":\"image/png\",\"fileSize\":2048}")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UP002"));
        mockMvc.perform(request("{\"fileName\":\"x.png\",\"contentType\":\"image/png\",\"fileSize\":5242881}")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UP003"));
        mockMvc.perform(request("{\"fileName\":\"x.png\",\"contentType\":\"image/png\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    private static MockHttpServletRequestBuilder request(String body) {
        return post(URL).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
