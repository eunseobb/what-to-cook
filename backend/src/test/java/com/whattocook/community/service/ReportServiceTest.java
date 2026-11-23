package com.whattocook.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.whattocook.community.domain.Comment;
import com.whattocook.community.domain.Report;
import com.whattocook.community.domain.ReportTargetType;
import com.whattocook.community.dto.ReportCreateRequest;
import com.whattocook.community.repository.CommentRepository;
import com.whattocook.community.repository.ReportRepository;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final int THRESHOLD = 3;

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserRepository userRepository;

    private ReportService reportService;

    private final User writer = withId(new User("writer@example.com", "hashed", "작성자"), 1L);
    private final User reporter = withId(new User("reporter@example.com", "hashed", "신고자"), 2L);
    private final Recipe recipe = withId(Recipe.builder()
            .author(writer).source(RecipeSource.COMMUNITY).title("파달걀볶음")
            .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
            .build(), 5L);

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, recipeRepository, commentRepository, userRepository,
                new ReportProperties(THRESHOLD));
    }

    @Test
    @DisplayName("기준값 미만이면 신고만 쌓이고 레시피는 게시 상태로 남는다")
    void reportBelowThreshold() {
        givenRecipeReport(THRESHOLD - 1);

        Long id = reportService.report(2L, new ReportCreateRequest(ReportTargetType.RECIPE, 5L));

        assertThat(id).isEqualTo(100L);
        assertThat(recipe.getStatus()).isEqualTo(RecipeStatus.PUBLISHED);
    }

    @Test
    @DisplayName("누적 신고 수가 기준값에 이르면 레시피를 숨긴다")
    void hideRecipeAtThreshold() {
        givenRecipeReport(THRESHOLD);

        reportService.report(2L, new ReportCreateRequest(ReportTargetType.RECIPE, 5L));

        assertThat(recipe.getStatus()).isEqualTo(RecipeStatus.HIDDEN);
    }

    @Test
    @DisplayName("누적 신고 수가 기준값에 이르면 댓글을 숨긴다")
    void hideCommentAtThreshold() {
        Comment comment = withId(new Comment(recipe, writer, null, "광고"), 10L);
        given(commentRepository.findByIdForUpdate(10L)).willReturn(Optional.of(comment));
        given(userRepository.getReferenceById(2L)).willReturn(reporter);
        given(reportRepository.save(any(Report.class))).willAnswer(inv -> withId(inv.getArgument(0), 100L));
        given(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.COMMENT, 10L)).willReturn((long) THRESHOLD);

        reportService.report(2L, new ReportCreateRequest(ReportTargetType.COMMENT, 10L));

        assertThat(comment.isHidden()).isTrue();
    }

    @Test
    @DisplayName("같은 대상을 다시 신고하면 거부한다")
    void duplicate() {
        given(recipeRepository.findByIdForUpdate(5L)).willReturn(Optional.of(recipe));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(2L, ReportTargetType.RECIPE, 5L))
                .willReturn(true);

        assertThatThrownBy(() -> reportService.report(2L, new ReportCreateRequest(ReportTargetType.RECIPE, 5L)))
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_REPORT);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("자신이 쓴 레시피·댓글은 신고할 수 없다")
    void own() {
        Comment comment = withId(new Comment(recipe, reporter, null, "내 댓글"), 10L);
        given(recipeRepository.findByIdForUpdate(5L)).willReturn(Optional.of(recipe));
        given(commentRepository.findByIdForUpdate(10L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> reportService.report(1L, new ReportCreateRequest(ReportTargetType.RECIPE, 5L)))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_REPORT_OWN);
        assertThatThrownBy(() -> reportService.report(2L, new ReportCreateRequest(ReportTargetType.COMMENT, 10L)))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_REPORT_OWN);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("게시 중이 아닌 레시피, 삭제·숨김된 댓글은 신고할 수 없다")
    void invisibleTarget() {
        Recipe hidden = withId(Recipe.builder()
                .author(writer).source(RecipeSource.COMMUNITY).title("숨긴 레시피")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1).build(), 6L);
        hidden.hide();
        Comment deleted = withId(new Comment(recipe, writer, null, "지움"), 10L);
        deleted.delete();
        Comment hiddenComment = withId(new Comment(recipe, writer, null, "숨김"), 11L);
        hiddenComment.hide();
        given(recipeRepository.findByIdForUpdate(6L)).willReturn(Optional.of(hidden));
        given(commentRepository.findByIdForUpdate(10L)).willReturn(Optional.of(deleted));
        given(commentRepository.findByIdForUpdate(11L)).willReturn(Optional.of(hiddenComment));

        assertThatThrownBy(() -> reportService.report(2L, new ReportCreateRequest(ReportTargetType.RECIPE, 6L)))
                .extracting("errorCode").isEqualTo(ErrorCode.RECIPE_NOT_FOUND);
        assertThatThrownBy(() -> reportService.report(2L, new ReportCreateRequest(ReportTargetType.COMMENT, 10L)))
                .extracting("errorCode").isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        assertThatThrownBy(() -> reportService.report(2L, new ReportCreateRequest(ReportTargetType.COMMENT, 11L)))
                .extracting("errorCode").isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    private void givenRecipeReport(long countAfterSave) {
        given(recipeRepository.findByIdForUpdate(5L)).willReturn(Optional.of(recipe));
        given(userRepository.getReferenceById(2L)).willReturn(reporter);
        given(reportRepository.save(any(Report.class))).willAnswer(inv -> withId(inv.getArgument(0), 100L));
        given(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.RECIPE, 5L)).willReturn(countAfterSave);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
