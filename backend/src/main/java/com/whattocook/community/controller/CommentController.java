package com.whattocook.community.controller;

import com.whattocook.community.dto.CommentCreateRequest;
import com.whattocook.community.dto.CommentCreateResponse;
import com.whattocook.community.dto.CommentResponse;
import com.whattocook.community.dto.CommentUpdateRequest;
import com.whattocook.community.service.CommentService;
import com.whattocook.global.common.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Comment", description = "레시피 댓글·대댓글")
@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 목록",
            description = "최상위 댓글을 오래된 순으로 커서 페이지네이션하고, 각 댓글에 대댓글을 붙여 준다. "
                    + "다음 페이지는 응답의 nextCursor를 cursor로 보낸다.")
    @SecurityRequirements
    @GetMapping("/recipes/{recipeId}/comments")
    public CursorPageResponse<CommentResponse> list(
            @PathVariable Long recipeId,
            @Parameter(description = "이전 페이지의 nextCursor") @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return commentService.list(recipeId, cursor, size);
    }

    @Operation(summary = "댓글 등록", description = "parentId를 보내면 대댓글. 대댓글에는 답글을 달 수 없다.")
    @PostMapping("/recipes/{recipeId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentCreateResponse create(@AuthenticationPrincipal Long userId, @PathVariable Long recipeId,
                                        @Valid @RequestBody CommentCreateRequest request) {
        return new CommentCreateResponse(commentService.create(userId, recipeId, request));
    }

    @Operation(summary = "댓글 수정", description = "작성자만 가능.")
    @PatchMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@AuthenticationPrincipal Long userId, @PathVariable Long commentId,
                       @Valid @RequestBody CommentUpdateRequest request) {
        commentService.update(userId, commentId, request.content());
    }

    @Operation(summary = "댓글 삭제", description = "작성자만 가능. 대댓글이 있으면 \"삭제된 댓글\"로 남는다.")
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long commentId) {
        commentService.delete(userId, commentId);
    }
}
