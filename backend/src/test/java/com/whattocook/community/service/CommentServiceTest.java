package com.whattocook.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.whattocook.community.domain.Comment;
import com.whattocook.community.dto.CommentCreateRequest;
import com.whattocook.community.dto.CommentResponse;
import com.whattocook.community.repository.CommentRepository;
import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommentService commentService;

    private final User writer = withId(new User("writer@example.com", "hashed", "작성자"), 1L);
    private final User other = withId(new User("other@example.com", "hashed", "다른사람"), 2L);
    private final Recipe recipe = withId(Recipe.builder()
            .author(writer).source(RecipeSource.COMMUNITY).title("파달걀볶음")
            .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
            .ingredients(List.of()).steps(List.of()).tags(List.of())
            .build(), 5L);

    @Test
    @DisplayName("목록은 최상위 댓글 페이지에 대댓글을 한 번에 붙이고, 삭제된 댓글은 내용·작성자를 숨긴다")
    void list() {
        Comment root1 = withId(new Comment(recipe, writer, null, "맛있어요"), 10L);
        Comment root2 = withId(new Comment(recipe, other, null, "지운 댓글"), 11L);
        root2.delete();
        Comment root3 = withId(new Comment(recipe, writer, null, "다음 페이지"), 12L);
        Comment reply = withId(new Comment(recipe, writer, root2, "답글"), 13L);
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe));
        given(commentRepository.findRootPage(5L, 0L, Limit.of(3))).willReturn(List.of(root1, root2, root3));
        given(commentRepository.findRepliesByParentIds(List.of(10L, 11L, 12L))).willReturn(List.of(reply));

        CursorPageResponse<CommentResponse> page = commentService.list(5L, null, 2);

        assertThat(page.items()).extracting(CommentResponse::id).containsExactly(10L, 11L);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("11");
        assertThat(page.items().get(0).replies()).isEmpty();
        CommentResponse deleted = page.items().get(1);
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.content()).isEqualTo(CommentResponse.DELETED_CONTENT);
        assertThat(deleted.authorId()).isNull();
        assertThat(deleted.replies()).extracting(CommentResponse::content).containsExactly("답글");
    }

    @Test
    @DisplayName("게시 중이 아닌 레시피의 댓글은 조회할 수 없다")
    void listOfMissingRecipe() {
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.list(5L, null, 20))
                .extracting("errorCode").isEqualTo(ErrorCode.RECIPE_NOT_FOUND);
    }

    @Test
    @DisplayName("최상위 댓글에 대댓글을 단다")
    void createReply() {
        Comment root = withId(new Comment(recipe, other, null, "질문"), 10L);
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe));
        given(commentRepository.findById(10L)).willReturn(Optional.of(root));
        given(userRepository.getReferenceById(1L)).willReturn(writer);
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> withId(inv.getArgument(0), 20L));

        Long id = commentService.create(1L, 5L, new CommentCreateRequest(" 답변 ", 10L));

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(id).isEqualTo(20L);
        assertThat(captor.getValue().getParent()).isSameAs(root);
        assertThat(captor.getValue().getContent()).isEqualTo("답변");
    }

    @Test
    @DisplayName("대댓글에는 답글을 달 수 없다")
    void replyToReply() {
        Comment root = withId(new Comment(recipe, other, null, "질문"), 10L);
        Comment reply = withId(new Comment(recipe, writer, root, "답변"), 11L);
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe));
        given(commentRepository.findById(11L)).willReturn(Optional.of(reply));

        assertThatThrownBy(() -> commentService.create(1L, 5L, new CommentCreateRequest("또 답변", 11L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REPLY_DEPTH_EXCEEDED);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 레시피의 댓글이나 삭제된 댓글에는 대댓글을 달 수 없다")
    void invalidParent() {
        Recipe otherRecipe = withId(Recipe.builder()
                .author(other).source(RecipeSource.COMMUNITY).title("다른 레시피")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1).build(), 6L);
        Comment elsewhere = withId(new Comment(otherRecipe, other, null, "다른 곳"), 10L);
        Comment deleted = withId(new Comment(recipe, other, null, "지움"), 11L);
        deleted.delete();
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe));
        given(commentRepository.findById(10L)).willReturn(Optional.of(elsewhere));
        given(commentRepository.findById(11L)).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> commentService.create(1L, 5L, new CommentCreateRequest("답변", 10L)))
                .extracting("errorCode").isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        assertThatThrownBy(() -> commentService.create(1L, 5L, new CommentCreateRequest("답변", 11L)))
                .extracting("errorCode").isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("작성자만 수정·삭제할 수 있고, 삭제는 소프트 삭제다")
    void updateAndDelete() {
        Comment comment = withId(new Comment(recipe, writer, null, "처음"), 10L);
        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(2L, 10L, "남의 글"))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_COMMENT_AUTHOR);
        assertThatThrownBy(() -> commentService.delete(2L, 10L))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_COMMENT_AUTHOR);

        commentService.update(1L, 10L, " 고침 ");
        assertThat(comment.getContent()).isEqualTo("고침");

        commentService.delete(1L, 10L);
        assertThat(comment.isDeleted()).isTrue();
        verify(commentRepository, never()).delete(any());

        assertThatThrownBy(() -> commentService.update(1L, 10L, "삭제 후"))
                .extracting("errorCode").isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
