package com.whattocook.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.domain.RecipeStep;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;
import com.whattocook.recipe.dto.RecipeCreateRequest;
import com.whattocook.recipe.dto.RecipeIngredientRequest;
import com.whattocook.recipe.dto.RecipeStepRequest;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import com.whattocook.recipe.dto.TagResponse;
import com.whattocook.recipe.dto.RecipeUpdateRequest;
import com.whattocook.recipe.domain.RecipeSort;
import com.whattocook.recipe.repository.RecipeCursor;
import com.whattocook.recipe.repository.RecipeListCondition;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recipe.repository.TagRepository;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RecipeService recipeService;

    private final User author = withId(new User("chef@example.com", "hashed", "요리사"), 1L);
    private final Ingredient greenOnion = withId(Ingredient.builder()
            .name("대파").category(IngredientCategory.VEGETABLE).baseUnit("g").build(), 10L);
    private final Ingredient egg = withId(Ingredient.builder()
            .name("달걀").category(IngredientCategory.DAIRY_EGG).baseUnit("개").build(), 11L);
    private final Tag korean = withId(new Tag(TagType.CUISINE, "한식"), 100L);

    @Test
    @DisplayName("생성 시 재료를 표준 재료로, 태그를 태그 마스터로 매핑해 커뮤니티 레시피로 저장한다")
    void create() {
        given(userRepository.getReferenceById(1L)).willReturn(author);
        given(ingredientRepository.findAllById(List.of(11L, 10L))).willReturn(List.of(greenOnion, egg));
        given(tagRepository.findAllById(List.of(100L))).willReturn(List.of(korean));
        given(recipeRepository.save(any(Recipe.class))).willAnswer(inv -> withId(inv.getArgument(0), 5L));

        Long id = recipeService.create(1L, createRequest(List.of(
                new RecipeIngredientRequest(11L, new BigDecimal("2"), "개"),
                new RecipeIngredientRequest(10L, null, " "))));

        ArgumentCaptor<Recipe> captor = ArgumentCaptor.forClass(Recipe.class);
        verify(recipeRepository).save(captor.capture());
        Recipe saved = captor.getValue();
        assertThat(id).isEqualTo(5L);
        assertThat(saved.getSource()).isEqualTo(RecipeSource.COMMUNITY);
        assertThat(saved.getStatus()).isEqualTo(RecipeStatus.PUBLISHED);
        assertThat(saved.getTitle()).isEqualTo("파달걀볶음");
        assertThat(saved.getIngredients()).extracting(i -> i.getIngredient().getName()).containsExactly("달걀", "대파");
        assertThat(saved.getIngredients().get(1).getUnit()).isNull();
        assertThat(saved.getSteps()).extracting(RecipeStep::getContent).containsExactly("볶는다");
        assertThat(saved.getTags()).containsExactly(korean);
    }

    @Test
    @DisplayName("없는 재료가 섞여 있으면 저장하지 않는다")
    void createWithUnknownIngredient() {
        given(userRepository.getReferenceById(1L)).willReturn(author);
        given(ingredientRepository.findAllById(List.of(10L, 99L))).willReturn(List.of(greenOnion));

        assertThatThrownBy(() -> recipeService.create(1L, createRequest(List.of(
                new RecipeIngredientRequest(10L, null, null), new RecipeIngredientRequest(99L, null, null)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECIPE_INGREDIENT_NOT_FOUND);
        verify(recipeRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 재료를 두 번 넣으면 저장하지 않는다")
    void createWithDuplicateIngredient() {
        given(userRepository.getReferenceById(1L)).willReturn(author);

        assertThatThrownBy(() -> recipeService.create(1L, createRequest(List.of(
                new RecipeIngredientRequest(10L, null, null), new RecipeIngredientRequest(10L, null, null)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RECIPE_INGREDIENT);
        verify(recipeRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 태그가 섞여 있으면 저장하지 않는다")
    void createWithUnknownTag() {
        given(userRepository.getReferenceById(1L)).willReturn(author);
        given(ingredientRepository.findAllById(anyCollection())).willReturn(List.of(greenOnion));
        given(tagRepository.findAllById(List.of(100L, 999L))).willReturn(List.of(korean));

        RecipeCreateRequest request = new RecipeCreateRequest("파볶음", null, Difficulty.EASY, 10, 1,
                List.of(new RecipeIngredientRequest(10L, null, null)), List.of(new RecipeStepRequest("볶는다", null)),
                List.of(100L, 999L, 100L));

        assertThatThrownBy(() -> recipeService.create(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECIPE_TAG_NOT_FOUND);
    }

    @Test
    @DisplayName("AI 레시피를 내 커뮤니티 레시피로 복사하고, 커뮤니티 레시피는 가져올 수 없다")
    void forkAiRecipe() {
        Recipe ai = withId(Recipe.builder().source(RecipeSource.AI_GENERATED).ingredientSetHash("hash")
                .title("AI 파달걀").difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(2)
                .ingredients(List.of(new RecipeIngredient(egg, new BigDecimal("2"), "개")))
                .steps(List.of(new RecipeStep("볶는다", null))).tags(List.of(korean)).build(), 7L);
        given(recipeRepository.findByIdAndStatus(7L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(ai));
        given(userRepository.getReferenceById(1L)).willReturn(author);
        given(recipeRepository.save(any(Recipe.class))).willAnswer(inv -> withId(inv.getArgument(0), 8L));

        assertThat(recipeService.forkAiRecipe(1L, 7L)).isEqualTo(8L);

        ArgumentCaptor<Recipe> captor = ArgumentCaptor.forClass(Recipe.class);
        verify(recipeRepository).save(captor.capture());
        Recipe copy = captor.getValue();
        assertThat(copy.getSource()).isEqualTo(RecipeSource.COMMUNITY);
        assertThat(copy.getIngredientSetHash()).isNull();
        assertThat(copy.isWrittenBy(1L)).isTrue();
        assertThat(copy.getTitle()).isEqualTo("AI 파달걀");
        assertThat(copy.getServings()).isEqualTo(2);
        assertThat(copy.getIngredients()).extracting(i -> i.getIngredient().getName()).containsExactly("달걀");
        assertThat(copy.getSteps()).extracting(RecipeStep::getContent).containsExactly("볶는다");
        assertThat(copy.getTags()).containsExactly(korean);

        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe(5L)));
        assertThatThrownBy(() -> recipeService.forkAiRecipe(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_AI_RECIPE);
    }

    @Test
    @DisplayName("게시 상태가 아니거나 없는 레시피는 RECIPE_NOT_FOUND")
    void getNotFound() {
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recipeService.get(5L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECIPE_NOT_FOUND);
    }

    @Test
    @DisplayName("목록은 size + 1건을 조회해 다음 커서를 만들고, 항목마다 대표 태그를 담는다")
    void list() {
        Recipe tagged = recipe(9L);
        tagged.replaceTags(List.of(korean));
        given(recipeRepository.search(new RecipeListCondition(List.of(), RecipeSort.LATEST, null), null, 3))
                .willReturn(List.of(tagged, recipe(8L), recipe(7L)));

        CursorPageResponse<RecipeSummaryResponse> page = recipeService.list(null, null, null, null, 2);

        assertThat(page.items()).extracting(RecipeSummaryResponse::id).containsExactly(9L, 8L);
        assertThat(page.items().get(0).authorNickname()).isEqualTo("요리사");
        assertThat(page.items().get(0).representativeTag())
                .isEqualTo(new TagResponse(100L, TagType.CUISINE, "한식", "accent-sage"));
        assertThat(page.items().get(1).representativeTag()).isNull();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("8");
    }

    @Test
    @DisplayName("인기순은 커서를 (좋아요 수, 작성 시각, id)로 풀고, 다음 커서도 같은 형식으로 만든다")
    void listPopular() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 20, 9, 30, 15, 123_000);
        Recipe top = recipe(9L);
        Recipe next = recipe(8L);
        ReflectionTestUtils.setField(next, "likeCount", 4);
        ReflectionTestUtils.setField(next, "createdAt", createdAt);
        ArgumentCaptor<RecipeListCondition> condition = ArgumentCaptor.forClass(RecipeListCondition.class);
        given(recipeRepository.search(condition.capture(), eq(new RecipeCursor(7, createdAt, 10L)), eq(3)))
                .willReturn(List.of(top, next, recipe(7L)));

        CursorPageResponse<RecipeSummaryResponse> page = recipeService.list(
                List.of(100L, 100L), RecipeSort.POPULAR, 7, "7_" + createdAt + "_10", 2);

        assertThat(page.nextCursor()).isEqualTo("4_2026-11-20T09:30:15.000123_8");
        assertThat(condition.getValue().tagIds()).containsExactly(100L);
        assertThat(condition.getValue().createdAfter())
                .isBetween(LocalDateTime.now().minusDays(7).minusMinutes(1), LocalDateTime.now().minusDays(7));
    }

    @Test
    @DisplayName("정렬과 맞지 않거나 깨진 커서는 잘못된 입력으로 거절한다")
    void listInvalidCursor() {
        assertThatThrownBy(() -> recipeService.list(null, RecipeSort.POPULAR, null, "8", 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> recipeService.list(null, RecipeSort.LATEST, null, "4_x_8", 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("수정은 보낸 필드만 바꾸고, 빈 대표 이미지 URL은 이미지를 지운다")
    void partialUpdate() {
        Recipe recipe = recipe(5L);
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe));

        recipeService.update(1L, 5L, new RecipeUpdateRequest(" 새 제목 ", "", null, 30, null,
                null, List.of(new RecipeStepRequest("새 단계", null)), null));

        assertThat(recipe.getTitle()).isEqualTo("새 제목");
        assertThat(recipe.getMainImageUrl()).isNull();
        assertThat(recipe.getCookTimeMinutes()).isEqualTo(30);
        assertThat(recipe.getServings()).isEqualTo(2);
        assertThat(recipe.getIngredients()).hasSize(1);
        assertThat(recipe.getSteps()).extracting(RecipeStep::getContent).containsExactly("새 단계");
    }

    @Test
    @DisplayName("작성자가 아니면 수정·삭제할 수 없다")
    void notAuthor() {
        Recipe recipe = recipe(5L);
        given(recipeRepository.findByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(Optional.of(recipe));
        RecipeUpdateRequest request = new RecipeUpdateRequest("탈취", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> recipeService.update(2L, 5L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RECIPE_AUTHOR);
        assertThatThrownBy(() -> recipeService.delete(2L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RECIPE_AUTHOR);
        assertThat(recipe.getTitle()).isEqualTo("파달걀볶음");
        assertThat(recipe.getStatus()).isEqualTo(RecipeStatus.PUBLISHED);
    }

    @Test
    @DisplayName("삭제는 소프트 삭제(DELETED)로 처리한다")
    void delete() {
        Recipe recipe = recipe(5L);
        given(recipeRepository.findByIdAndStatus(eq(5L), eq(RecipeStatus.PUBLISHED))).willReturn(Optional.of(recipe));

        recipeService.delete(1L, 5L);

        assertThat(recipe.getStatus()).isEqualTo(RecipeStatus.DELETED);
        verify(recipeRepository, never()).delete(any());
    }

    private static RecipeCreateRequest createRequest(List<RecipeIngredientRequest> ingredients) {
        return new RecipeCreateRequest(" 파달걀볶음 ", "https://img.example.com/main.jpg", Difficulty.EASY, 10, 1,
                ingredients, List.of(new RecipeStepRequest(" 볶는다 ", null)), List.of(100L));
    }

    private Recipe recipe(Long id) {
        return withId(Recipe.builder()
                .author(author).source(RecipeSource.COMMUNITY).title("파달걀볶음")
                .mainImageUrl("https://img.example.com/main.jpg")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(2)
                .ingredients(List.of(new RecipeIngredient(egg, null, null)))
                .steps(List.of(new RecipeStep("볶는다", null)))
                .build(), id);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
