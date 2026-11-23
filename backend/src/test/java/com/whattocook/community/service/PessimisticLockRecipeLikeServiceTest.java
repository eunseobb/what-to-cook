package com.whattocook.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.whattocook.community.domain.RecipeLike;
import com.whattocook.community.repository.RecipeLikeRepository;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PessimisticLockRecipeLikeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeLikeRepository recipeLikeRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PessimisticLockRecipeLikeService service;

    private final Recipe recipe = Recipe.builder().source(RecipeSource.COMMUNITY).title("파달걀볶음")
            .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1).build();

    @Test
    @DisplayName("락을 잡은 레시피에 좋아요를 저장하고 카운트를 바로 올린다")
    void like() {
        given(recipeRepository.findForUpdateByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe));
        given(recipeLikeRepository.existsByUserIdAndRecipeId(1L, 5L)).willReturn(false);

        service.like(1L, 5L);

        verify(recipeLikeRepository).save(any(RecipeLike.class));
        assertThat(recipe.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 좋아요했으면 L001, 게시 중이 아닌 레시피면 R001")
    void likeRejected() {
        given(recipeRepository.findForUpdateByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe));
        given(recipeLikeRepository.existsByUserIdAndRecipeId(1L, 5L)).willReturn(true);
        assertThatThrownBy(() -> service.like(1L, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_LIKED);

        given(recipeRepository.findForUpdateByIdAndStatus(6L, RecipeStatus.PUBLISHED)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.like(1L, 6L))
                .extracting("errorCode").isEqualTo(ErrorCode.RECIPE_NOT_FOUND);
        verify(recipeLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("좋아요 취소는 행을 지운 경우에만 카운트를 내리고, 지운 행이 없으면 L002")
    void unlike() {
        recipe.increaseLikeCount();
        given(recipeRepository.findForUpdateByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe));
        given(recipeLikeRepository.deleteByUserIdAndRecipeId(1L, 5L)).willReturn(1, 0);

        service.unlike(1L, 5L);
        assertThat(recipe.getLikeCount()).isZero();

        assertThatThrownBy(() -> service.unlike(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LIKE_NOT_FOUND);
        assertThat(recipe.getLikeCount()).isZero();
    }
}
