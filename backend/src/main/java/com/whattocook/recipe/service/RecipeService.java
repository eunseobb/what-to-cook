package com.whattocook.recipe.service;

import com.whattocook.global.common.CursorPageResponse;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.domain.RecipeStep;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.dto.RecipeCreateRequest;
import com.whattocook.recipe.dto.RecipeDetailResponse;
import com.whattocook.recipe.dto.RecipeIngredientRequest;
import com.whattocook.recipe.dto.RecipeStepRequest;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import com.whattocook.recipe.dto.RecipeUpdateRequest;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recipe.repository.TagRepository;
import com.whattocook.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커뮤니티 레시피 CRUD. 조회는 게시 상태만, 수정·삭제는 작성자만 할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final TagRepository tagRepository;
    private final IngredientRepository ingredientRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long create(Long userId, RecipeCreateRequest request) {
        Recipe recipe = Recipe.builder()
                .author(userRepository.getReferenceById(userId))
                .source(RecipeSource.COMMUNITY)
                .title(request.title())
                .mainImageUrl(request.mainImageUrl())
                .difficulty(request.difficulty())
                .cookTimeMinutes(request.cookTimeMinutes())
                .servings(request.servings())
                .ingredients(toIngredients(request.ingredients()))
                .steps(toSteps(request.steps()))
                .tags(findTags(request.tagIds()))
                .build();
        return recipeRepository.save(recipe).getId();
    }

    /**
     * AI 레시피를 내 커뮤니티 레시피로 복사한다. 이후 편집은 일반 수정 API로 한다.
     * 원본 AI 레시피는 다른 사용자도 재사용하므로 건드리지 않는다.
     */
    @Transactional
    public Long forkAiRecipe(Long userId, Long recipeId) {
        Recipe source = findPublished(recipeId);
        if (source.getSource() != RecipeSource.AI_GENERATED) {
            throw new BusinessException(ErrorCode.NOT_AI_RECIPE);
        }
        Recipe copy = Recipe.builder()
                .author(userRepository.getReferenceById(userId))
                .source(RecipeSource.COMMUNITY)
                .title(source.getTitle())
                .mainImageUrl(source.getMainImageUrl())
                .difficulty(source.getDifficulty())
                .cookTimeMinutes(source.getCookTimeMinutes())
                .servings(source.getServings())
                .ingredients(source.getIngredients().stream()
                        .map(i -> new RecipeIngredient(i.getIngredient(), i.getQuantity(), i.getUnit())).toList())
                .steps(source.getSteps().stream().map(s -> new RecipeStep(s.getContent(), s.getImageUrl())).toList())
                .tags(source.getTags())
                .build();
        return recipeRepository.save(copy).getId();
    }

    public RecipeDetailResponse get(Long recipeId) {
        return RecipeDetailResponse.from(findPublished(recipeId));
    }

    /** @param cursor 이전 페이지의 nextCursor(마지막 레시피 id). 첫 페이지는 null. */
    public CursorPageResponse<RecipeSummaryResponse> list(Long cursor, int size) {
        List<RecipeSummaryResponse> fetched = recipeRepository.findByStatusAndIdLessThanOrderByIdDesc(
                        RecipeStatus.PUBLISHED, cursor == null ? Long.MAX_VALUE : cursor, Limit.of(size + 1))
                .stream().map(RecipeSummaryResponse::from).toList();
        return CursorPageResponse.of(fetched, size, r -> String.valueOf(r.id()));
    }

    @Transactional
    public void update(Long userId, Long recipeId, RecipeUpdateRequest request) {
        Recipe recipe = findOwned(userId, recipeId);
        if (request.title() != null) {
            recipe.changeTitle(request.title());
        }
        if (request.mainImageUrl() != null) {
            recipe.changeMainImageUrl(request.mainImageUrl().isEmpty() ? null : request.mainImageUrl());
        }
        if (request.difficulty() != null) {
            recipe.changeDifficulty(request.difficulty());
        }
        if (request.cookTimeMinutes() != null) {
            recipe.changeCookTimeMinutes(request.cookTimeMinutes());
        }
        if (request.servings() != null) {
            recipe.changeServings(request.servings());
        }
        if (request.ingredients() != null) {
            recipe.replaceIngredients(toIngredients(request.ingredients()));
        }
        if (request.steps() != null) {
            recipe.replaceSteps(toSteps(request.steps()));
        }
        if (request.tagIds() != null) {
            recipe.replaceTags(findTags(request.tagIds()));
        }
    }

    @Transactional
    public void delete(Long userId, Long recipeId) {
        findOwned(userId, recipeId).delete();
    }

    private Recipe findPublished(Long recipeId) {
        return recipeRepository.findByIdAndStatus(recipeId, RecipeStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
    }

    private Recipe findOwned(Long userId, Long recipeId) {
        Recipe recipe = findPublished(recipeId);
        if (!recipe.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RECIPE_AUTHOR);
        }
        return recipe;
    }

    private List<RecipeIngredient> toIngredients(List<RecipeIngredientRequest> requests) {
        List<Long> ids = requests.stream().map(RecipeIngredientRequest::ingredientId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RECIPE_INGREDIENT);
        }
        Map<Long, Ingredient> byId = ingredientRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));
        if (byId.size() != ids.size()) {
            throw new BusinessException(ErrorCode.RECIPE_INGREDIENT_NOT_FOUND);
        }
        return requests.stream()
                .map(r -> new RecipeIngredient(byId.get(r.ingredientId()), r.quantity(), r.unit()))
                .toList();
    }

    private static List<RecipeStep> toSteps(List<RecipeStepRequest> requests) {
        return requests.stream().map(r -> new RecipeStep(r.content(), r.imageUrl())).toList();
    }

    private List<Tag> findTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = tagIds.stream().distinct().toList();
        List<Tag> tags = tagRepository.findAllById(distinctIds);
        if (tags.size() != distinctIds.size()) {
            throw new BusinessException(ErrorCode.RECIPE_TAG_NOT_FOUND);
        }
        return tags;
    }
}
