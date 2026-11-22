package com.whattocook.community.dto;

import com.whattocook.community.domain.Comment;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 댓글 항목. 최상위 댓글은 replies에 대댓글을 담고, 대댓글의 replies는 비어 있다.
 * 삭제된 댓글(대댓글이 남은 경우만 내려간다)은 내용과 작성자를 숨기고 deleted = true로 표시한다.
 */
public record CommentResponse(
        Long id,
        String content,
        Long authorId,
        String authorNickname,
        boolean deleted,
        LocalDateTime createdAt,
        List<CommentResponse> replies) {

    public static final String DELETED_CONTENT = "삭제된 댓글";

    public static CommentResponse of(Comment comment, List<CommentResponse> replies) {
        if (comment.isDeleted()) {
            return new CommentResponse(comment.getId(), DELETED_CONTENT, null, null, true,
                    comment.getCreatedAt(), replies);
        }
        return new CommentResponse(comment.getId(), comment.getContent(), comment.getUser().getId(),
                comment.getUser().getNickname(), false, comment.getCreatedAt(), replies);
    }
}
