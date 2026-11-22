package com.whattocook.community.service;

import com.whattocook.community.repository.RecipeBookmarkRepository;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 레시피 저장(북마크). 저장·해제 모두 멱등이다: 이미 저장한 레시피를 다시 저장하거나
 * 저장하지 않은 레시피를 해제해도 에러 없이 같은 결과가 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecipeBookmarkService {

    private final RecipeBookmarkRepository recipeBookmarkRepository;
    private final RecipeRepository recipeRepository;

    /** 게시 중인 레시피만 저장할 수 있다. 삭제된 레시피는 없는 레시피로 본다. */
    public void bookmark(Long userId, Long recipeId) {
        if (recipeRepository.findByIdAndStatus(recipeId, RecipeStatus.PUBLISHED).isEmpty()) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        recipeBookmarkRepository.insertIfAbsent(userId, recipeId);
    }

    /** 레시피가 삭제됐어도 해제는 할 수 있게 레시피 상태를 확인하지 않는다. */
    public void unbookmark(Long userId, Long recipeId) {
        recipeBookmarkRepository.deleteByUserIdAndRecipeId(userId, recipeId);
    }
}
