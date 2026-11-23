package com.whattocook.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whattocook.community.domain.Comment;
import com.whattocook.community.domain.ReportTargetType;
import com.whattocook.community.dto.ReportCreateRequest;
import com.whattocook.community.repository.CommentRepository;
import com.whattocook.community.repository.ReportRepository;
import com.whattocook.community.service.ReportService;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.global.security.JwtTokenProvider;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 신고 → 기준값(테스트 설정 3) 도달 시 숨김 흐름과 동시 신고 정확성을 실제 DB로 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportIntegrationTest {

    private static final int THRESHOLD = 3;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private ReportService reportService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private ReportRepository reportRepository;

    private User writer;
    private List<User> reporters;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        writer = userRepository.save(new User("report-writer@example.com", "hashed", "작성자"));
        reporters = IntStream.range(0, 6)
                .mapToObj(i -> userRepository.save(new User("reporter" + i + "@example.com", "hashed", "신고자" + i)))
                .toList();
        recipe = recipeRepository.save(Recipe.builder()
                .author(writer).source(RecipeSource.COMMUNITY).title("파달걀볶음")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .build());
    }

    @AfterEach
    void tearDown() {
        reportRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        recipeRepository.deleteById(recipe.getId());
        userRepository.deleteAll(reporters);
        userRepository.delete(writer);
    }

    @Test
    @DisplayName("댓글 신고가 기준값에 이르면 \"숨겨진 댓글\"로 바뀌고 대댓글은 남는다. 중복·본인 신고는 거부한다")
    void hideComment() throws Exception {
        Comment root = commentRepository.save(new Comment(recipe, writer, null, "광고"));
        commentRepository.save(new Comment(recipe, reporters.get(0), root, "신고했어요"));
        String body = "{\"targetType\":\"COMMENT\",\"targetId\":%d}".formatted(root.getId());

        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        report(writer, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RP002"));
        report(reporters.get(0), body).andExpect(status().isCreated()).andExpect(jsonPath("$.reportId").isNumber());
        report(reporters.get(0), body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RP001"));
        report(reporters.get(1), body).andExpect(status().isCreated());

        String path = "/api/recipes/" + recipe.getId() + "/comments";
        mockMvc.perform(get(path)).andExpect(jsonPath("$.items[0].content").value("광고"));

        report(reporters.get(2), body).andExpect(status().isCreated());

        mockMvc.perform(get(path))
                .andExpect(jsonPath("$.items[0].id").value(root.getId()))
                .andExpect(jsonPath("$.items[0].hidden").value(true))
                .andExpect(jsonPath("$.items[0].content").value("숨겨진 댓글"))
                .andExpect(jsonPath("$.items[0].authorNickname").doesNotExist())
                .andExpect(jsonPath("$.items[0].replies[0].content").value("신고했어요"));
        report(reporters.get(3), body).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CM001"));
    }

    @Test
    @DisplayName("레시피 신고가 기준값에 이르면 숨겨져 상세 조회가 404가 된다")
    void hideRecipe() throws Exception {
        String body = "{\"targetType\":\"RECIPE\",\"targetId\":%d}".formatted(recipe.getId());
        for (int i = 0; i < THRESHOLD; i++) {
            mockMvc.perform(get("/api/recipes/{id}", recipe.getId())).andExpect(status().isOk());
            report(reporters.get(i), body).andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/recipes/{id}", recipe.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
        report(reporters.get(THRESHOLD), "{\"targetType\":\"SPAM\",\"targetId\":1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("여러 사용자가 동시에 신고해도 정확히 기준값에서 숨기고, 숨긴 뒤 신고는 받지 않는다")
    void concurrentReports() throws Exception {
        List<ErrorCode> results = runConcurrently(reporters.stream()
                .map(u -> (Callable<Long>) () -> reportService.report(u.getId(),
                        new ReportCreateRequest(ReportTargetType.RECIPE, recipe.getId())))
                .toList());

        assertThat(results).filteredOn(r -> r == null).hasSize(THRESHOLD);
        assertThat(results).filteredOn(r -> r != null).containsOnly(ErrorCode.RECIPE_NOT_FOUND);
        assertThat(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.RECIPE, recipe.getId()))
                .isEqualTo(THRESHOLD);
        assertThat(recipeRepository.findById(recipe.getId()).orElseThrow().getStatus())
                .isEqualTo(RecipeStatus.HIDDEN);
    }

    @Test
    @DisplayName("같은 사용자가 같은 대상을 동시에 여러 번 신고해도 한 번만 집계된다")
    void concurrentDuplicateReports() throws Exception {
        User reporter = reporters.get(0);
        List<ErrorCode> results = runConcurrently(IntStream.range(0, 5)
                .mapToObj(i -> (Callable<Long>) () -> reportService.report(reporter.getId(),
                        new ReportCreateRequest(ReportTargetType.RECIPE, recipe.getId())))
                .toList());

        assertThat(results).filteredOn(r -> r == null).hasSize(1);
        assertThat(results).filteredOn(r -> r != null).containsOnly(ErrorCode.DUPLICATE_REPORT);
        assertThat(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.RECIPE, recipe.getId()))
                .isEqualTo(1);
    }

    /** 작업들을 동시에 출발시키고, 각 결과를 성공이면 null, 실패면 에러 코드로 돌려준다. */
    private static List<ErrorCode> runConcurrently(List<Callable<Long>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (Callable<Long> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return null;
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            start.countDown();
            List<ErrorCode> results = new ArrayList<>();
            for (Future<ErrorCode> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    private ResultActions report(User user, String body) throws Exception {
        return mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(body)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(user.getId())));
    }
}
