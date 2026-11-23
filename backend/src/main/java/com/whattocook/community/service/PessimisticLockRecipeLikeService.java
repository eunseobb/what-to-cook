package com.whattocook.community.service;

import com.whattocook.community.domain.RecipeLike;
import com.whattocook.community.repository.RecipeLikeRepository;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비관적 락 방식. 레시피 행을 SELECT ... FOR UPDATE로 잡아 같은 레시피의 좋아요를 직렬화하고
 * recipe.like_count를 같은 트랜잭션에서 바로 갱신한다. 인기 레시피에 요청이 몰리면 행 락 대기가 길어진다.
 */
@Service
@ConditionalOnProperty(name = "app.like.strategy", havingValue = "lock")
@RequiredArgsConstructor
@Transactional
public class PessimisticLockRecipeLikeService implements RecipeLikeService {

    private final RecipeRepository recipeRepository;
    private final RecipeLikeRepository recipeLikeRepository;
    private final UserRepository userRepository;

    @Override
    public void like(Long userId, Long recipeId) {
        Recipe recipe = lockPublished(recipeId);
        if (recipeLikeRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        recipeLikeRepository.save(new RecipeLike(userRepository.getReferenceById(userId), recipe));
        recipe.increaseLikeCount();
    }

    @Override
    public void unlike(Long userId, Long recipeId) {
        Recipe recipe = lockPublished(recipeId);
        if (recipeLikeRepository.deleteByUserIdAndRecipeId(userId, recipeId) == 0) {
            throw new BusinessException(ErrorCode.LIKE_NOT_FOUND);
        }
        recipe.decreaseLikeCount();
    }

    private Recipe lockPublished(Long recipeId) {
        return recipeRepository.findForUpdateByIdAndStatus(recipeId, RecipeStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
    }
}
