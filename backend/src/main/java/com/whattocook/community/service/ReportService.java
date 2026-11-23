package com.whattocook.community.service;

import com.whattocook.community.domain.Comment;
import com.whattocook.community.domain.Report;
import com.whattocook.community.domain.ReportTargetType;
import com.whattocook.community.dto.ReportCreateRequest;
import com.whattocook.community.repository.CommentRepository;
import com.whattocook.community.repository.ReportRepository;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 레시피·댓글 신고. 누적 신고 수가 기준값에 이르면 대상을 숨긴다.
 * 대상 행을 먼저 잠가 같은 대상 신고를 한 줄로 세운다. 그래야 동시 신고에서도 중복 검사와 누적 수 집계가 정확하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final RecipeRepository recipeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ReportProperties reportProperties;

    @Transactional
    public Long report(Long userId, ReportCreateRequest request) {
        ReportTargetType type = request.targetType();
        Long targetId = request.targetId();
        Runnable hide = switch (type) {
            case RECIPE -> {
                Recipe recipe = lockPublishedRecipe(targetId);
                checkNotOwn(recipe.isWrittenBy(userId));
                yield recipe::hide;
            }
            case COMMENT -> {
                Comment comment = lockVisibleComment(targetId);
                checkNotOwn(comment.isWrittenBy(userId));
                yield comment::hide;
            }
        };
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(userId, type, targetId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }
        Long reportId = reportRepository.save(new Report(userRepository.getReferenceById(userId), type, targetId))
                .getId();
        if (reportRepository.countByTargetTypeAndTargetId(type, targetId) >= reportProperties.hideThreshold()) {
            hide.run();
        }
        return reportId;
    }

    private Recipe lockPublishedRecipe(Long recipeId) {
        return recipeRepository.findByIdForUpdate(recipeId)
                .filter(r -> r.getStatus() == RecipeStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
    }

    /** 신고할 수 있는 댓글: 삭제·숨김되지 않았고 게시 중인 레시피에 달린 댓글. */
    private Comment lockVisibleComment(Long commentId) {
        return commentRepository.findByIdForUpdate(commentId)
                .filter(c -> !c.isDeleted() && !c.isHidden() && c.getRecipe().getStatus() == RecipeStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private static void checkNotOwn(boolean own) {
        if (own) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_OWN);
        }
    }
}
