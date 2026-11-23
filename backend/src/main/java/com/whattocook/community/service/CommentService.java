package com.whattocook.community.service;

import com.whattocook.community.domain.Comment;
import com.whattocook.community.dto.CommentCreateRequest;
import com.whattocook.community.dto.CommentResponse;
import com.whattocook.community.repository.CommentRepository;
import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 레시피 댓글·대댓글. 게시 중인 레시피에만 달 수 있고, 수정·삭제는 작성자만 할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    /**
     * 최상위 댓글을 커서로 페이지 나누고, 그 댓글들의 대댓글은 한 번에 붙인다(쿼리 2번).
     *
     * @param cursor 이전 페이지의 nextCursor(마지막 최상위 댓글 id). 첫 페이지는 null.
     */
    public CursorPageResponse<CommentResponse> list(Long recipeId, Long cursor, int size) {
        findPublishedRecipe(recipeId);
        List<Comment> roots = commentRepository.findRootPage(recipeId, cursor == null ? 0L : cursor, Limit.of(size + 1));
        // shortcut: 대댓글은 페이지 없이 전부 내려준다. 댓글당 대댓글이 수백 건이 되면 별도 API로 나눈다.
        Map<Long, List<CommentResponse>> repliesByParent = roots.isEmpty() ? Map.of()
                : commentRepository.findRepliesByParentIds(roots.stream().map(Comment::getId).toList()).stream()
                        .collect(Collectors.groupingBy(r -> r.getParent().getId(),
                                Collectors.mapping(r -> CommentResponse.of(r, List.of()), Collectors.toList())));
        List<CommentResponse> fetched = roots.stream()
                .map(c -> CommentResponse.of(c, repliesByParent.getOrDefault(c.getId(), List.of())))
                .toList();
        return CursorPageResponse.of(fetched, size, c -> String.valueOf(c.id()));
    }

    @Transactional
    public Long create(Long userId, Long recipeId, CommentCreateRequest request) {
        Recipe recipe = findPublishedRecipe(recipeId);
        Comment parent = request.parentId() == null ? null : findReplyTarget(recipeId, request.parentId());
        Comment comment = new Comment(recipe, userRepository.getReferenceById(userId), parent, request.content());
        return commentRepository.save(comment).getId();
    }

    @Transactional
    public void update(Long userId, Long commentId, String content) {
        findOwned(userId, commentId).changeContent(content);
    }

    @Transactional
    public void delete(Long userId, Long commentId) {
        findOwned(userId, commentId).delete();
    }

    private Recipe findPublishedRecipe(Long recipeId) {
        return recipeRepository.findByIdAndStatus(recipeId, RecipeStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
    }

    /** 대댓글을 달 수 있는 부모: 같은 레시피의 삭제·숨김되지 않은 최상위 댓글. */
    private Comment findReplyTarget(Long recipeId, Long parentId) {
        Comment parent = commentRepository.findById(parentId)
                .filter(c -> !c.isDeleted() && !c.isHidden() && Objects.equals(c.getRecipe().getId(), recipeId))
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (parent.isReply()) {
            throw new BusinessException(ErrorCode.REPLY_DEPTH_EXCEEDED);
        }
        return parent;
    }

    private Comment findOwned(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .filter(c -> !c.isDeleted() && !c.isHidden() && c.getRecipe().getStatus() == RecipeStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_COMMENT_AUTHOR);
        }
        return comment;
    }
}
