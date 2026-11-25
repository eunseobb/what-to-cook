package com.whattocook.user.service;

import com.whattocook.community.domain.RecipeBookmark;
import com.whattocook.community.domain.RecipeLike;
import com.whattocook.community.repository.RecipeBookmarkRepository;
import com.whattocook.community.repository.RecipeLikeRepository;
import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import com.whattocook.recipe.repository.RecipeRepository;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 목록. 게시 중인 레시피만 보여 주므로 숨김·삭제된 레시피는 빠진다.
 * 커서는 내 레시피는 레시피 id, 저장·좋아요는 저장·좋아요 행 id(=한 순서)다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final RecipeRepository recipeRepository;
    private final RecipeBookmarkRepository recipeBookmarkRepository;
    private final RecipeLikeRepository recipeLikeRepository;

    public CursorPageResponse<RecipeSummaryResponse> myRecipes(Long userId, Long cursor, int size) {
        List<Recipe> fetched = recipeRepository.findByAuthorIdAndStatusAndIdLessThanOrderByIdDesc(
                userId, RecipeStatus.PUBLISHED, orMax(cursor), Limit.of(size + 1));
        return toSummaryPage(fetched, size, Recipe::getId, Function.identity());
    }

    public CursorPageResponse<RecipeSummaryResponse> bookmarks(Long userId, Long cursor, int size) {
        List<RecipeBookmark> fetched = recipeBookmarkRepository.findPageByUserId(
                userId, RecipeStatus.PUBLISHED, orMax(cursor), Limit.of(size + 1));
        return toSummaryPage(fetched, size, RecipeBookmark::getId, RecipeBookmark::getRecipe);
    }

    public CursorPageResponse<RecipeSummaryResponse> likes(Long userId, Long cursor, int size) {
        List<RecipeLike> fetched = recipeLikeRepository.findPageByUserId(
                userId, RecipeStatus.PUBLISHED, orMax(cursor), Limit.of(size + 1));
        return toSummaryPage(fetched, size, RecipeLike::getId, RecipeLike::getRecipe);
    }

    private static long orMax(Long cursor) {
        return cursor == null ? Long.MAX_VALUE : cursor;
    }

    private static <T> CursorPageResponse<RecipeSummaryResponse> toSummaryPage(
            List<T> fetched, int size, Function<T, Long> idOf, Function<T, Recipe> recipeOf) {
        CursorPageResponse<T> page = CursorPageResponse.of(fetched, size, e -> String.valueOf(idOf.apply(e)));
        List<RecipeSummaryResponse> items = page.items().stream()
                .map(recipeOf.andThen(RecipeSummaryResponse::from)).toList();
        return new CursorPageResponse<>(items, page.nextCursor(), page.hasNext());
    }
}
