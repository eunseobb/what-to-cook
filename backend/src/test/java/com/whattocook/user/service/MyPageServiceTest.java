package com.whattocook.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.whattocook.community.domain.RecipeBookmark;
import com.whattocook.community.repository.RecipeBookmarkRepository;
import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import com.whattocook.recipe.repository.RecipeRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeBookmarkRepository recipeBookmarkRepository;

    @InjectMocks
    private MyPageService myPageService;

    @Test
    @DisplayName("내 레시피: 첫 페이지는 최대 id부터 size+1건을 조회하고, 다음 커서는 마지막 레시피 id다")
    void myRecipes() {
        List<Recipe> fetched = List.of(recipe(30L), recipe(20L), recipe(10L));
        given(recipeRepository.findByAuthorIdAndStatusAndIdLessThanOrderByIdDesc(
                1L, RecipeStatus.PUBLISHED, Long.MAX_VALUE, Limit.of(3))).willReturn(fetched);

        CursorPageResponse<RecipeSummaryResponse> page = myPageService.myRecipes(1L, null, 2);

        assertThat(page.items()).extracting(RecipeSummaryResponse::id).containsExactly(30L, 20L);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("20");
    }

    @Test
    @DisplayName("저장 목록: 커서는 레시피 id가 아니라 저장 행 id다")
    void bookmarks() {
        RecipeBookmark newer = bookmark(9L, recipe(100L));
        RecipeBookmark older = bookmark(8L, recipe(200L));
        given(recipeBookmarkRepository.findPageByUserId(1L, RecipeStatus.PUBLISHED, 10L, Limit.of(2)))
                .willReturn(List.of(newer, older));

        CursorPageResponse<RecipeSummaryResponse> page = myPageService.bookmarks(1L, 10L, 1);

        assertThat(page.items()).extracting(RecipeSummaryResponse::id).containsExactly(100L);
        assertThat(page.nextCursor()).isEqualTo("9");
    }

    private static Recipe recipe(Long id) {
        Recipe recipe = lenientMock(Recipe.class);
        given(recipe.getId()).willReturn(id);
        return recipe;
    }

    private static RecipeBookmark bookmark(Long id, Recipe recipe) {
        RecipeBookmark bookmark = lenientMock(RecipeBookmark.class);
        given(bookmark.getId()).willReturn(id);
        given(bookmark.getRecipe()).willReturn(recipe);
        return bookmark;
    }

    // 한 페이지를 넘는 항목은 매핑되지 않으므로 쓰이지 않는 스텁을 허용한다.
    private static <T> T lenientMock(Class<T> type) {
        return mock(type, withSettings().strictness(Strictness.LENIENT));
    }
}
