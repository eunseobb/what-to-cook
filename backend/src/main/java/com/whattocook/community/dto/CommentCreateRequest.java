package com.whattocook.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param parentId 대댓글이면 최상위 댓글 id. 최상위 댓글이면 null.
 */
public record CommentCreateRequest(
        @NotBlank @Size(max = 500) String content,
        Long parentId) {
}
