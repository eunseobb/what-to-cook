package com.whattocook.community.dto;

import com.whattocook.community.domain.ReportTargetType;
import jakarta.validation.constraints.NotNull;

/**
 * @param targetType 신고 대상 종류 (RECIPE, COMMENT)
 * @param targetId   신고 대상 레시피 또는 댓글 id
 */
public record ReportCreateRequest(
        @NotNull ReportTargetType targetType,
        @NotNull Long targetId) {
}
