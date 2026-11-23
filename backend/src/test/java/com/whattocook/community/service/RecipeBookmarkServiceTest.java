package com.whattocook.community.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.whattocook.community.repository.RecipeBookmarkRepository;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeBookmarkServiceTest {

    @Mock
    private RecipeBookmarkRepository recipeBookmarkRepository;
    @Mock
    private RecipeRepository recipeRepository;

    @InjectMocks
    private RecipeBookmarkService recipeBookmarkService;

    @Test
    @DisplayName("게시 중인 레시피는 저장한다(이미 저장돼 있으면 insertIfAbsent가 무시한다)")
    void bookmark() {
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(mock(Recipe.class)));

        recipeBookmarkService.bookmark(1L, 5L);

        verify(recipeBookmarkRepository).insertIfAbsent(1L, 5L);
    }

    @Test
    @DisplayName("삭제됐거나 없는 레시피는 저장하지 않고 R001을 던진다")
    void bookmarkDeletedRecipe() {
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recipeBookmarkService.bookmark(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECIPE_NOT_FOUND);
        verify(recipeBookmarkRepository, never()).insertIfAbsent(anyLong(), anyLong());
    }

    @Test
    @DisplayName("해제는 레시피 상태와 관계없이 저장 행만 지운다")
    void unbookmark() {
        recipeBookmarkService.unbookmark(1L, 5L);

        verify(recipeBookmarkRepository).deleteByUserIdAndRecipeId(1L, 5L);
        verifyNoInteractions(recipeRepository);
    }
}
