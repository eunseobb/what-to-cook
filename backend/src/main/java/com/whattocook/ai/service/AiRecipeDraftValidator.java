package com.whattocook.ai.service;

import com.whattocook.ai.client.AiRecipeDraft;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.service.IngredientNormalizationResult;
import com.whattocook.ingredient.service.IngredientNormalizationService;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStep;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 초안을 검증해 저장할 AI 레시피로 바꾼다. 하나라도 어긋난 초안은 통째로 버린다(재사용되는 데이터라 품질을 우선한다).
 * - 형식: 제목·분량·단계 수 등 Bean Validation
 * - 재료 마스터에 없는 재료나 같은 재료가 두 번 나오면 버린다 (매칭 실패 입력은 정규화 서비스가 로그로 남긴다)
 * - 사용자 재료를 하나도 쓰지 않으면 버린다
 * - 1인분 분량이 비현실적이면 버린다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRecipeDraftValidator {

    static final int MAX_RECIPES = 3;
    /** 단위별 1인분 최대 분량. 목록에 없는 단위(개, 큰술, 컵 등)는 DEFAULT_MAX_PER_SERVING을 쓴다. */
    private static final Map<String, BigDecimal> MAX_PER_SERVING = Map.of(
            "g", new BigDecimal("1000"), "ml", new BigDecimal("1000"),
            "kg", BigDecimal.ONE, "l", BigDecimal.ONE);
    private static final BigDecimal DEFAULT_MAX_PER_SERVING = BigDecimal.TEN;

    private final Validator validator;
    private final IngredientNormalizationService normalizationService;

    public List<Recipe> toRecipes(List<AiRecipeDraft> drafts, Collection<Long> requestedIngredientIds,
                                  String ingredientSetHash) {
        List<Recipe> recipes = new ArrayList<>();
        for (AiRecipeDraft draft : drafts) {
            if (recipes.size() == MAX_RECIPES) {
                break;
            }
            String reason = rejectReason(draft);
            List<Ingredient> ingredients = null;
            if (reason == null) {
                ingredients = matchIngredients(draft);
                if (ingredients == null) {
                    reason = "재료 마스터에 없거나 중복된 재료";
                } else if (ingredients.stream().noneMatch(i -> requestedIngredientIds.contains(i.getId()))) {
                    reason = "사용자 재료를 쓰지 않음";
                }
            }
            if (reason != null) {
                log.info("AI 레시피 초안 제외({}): {}", reason, draft == null ? null : draft.title());
                continue;
            }
            recipes.add(toRecipe(draft, ingredients, ingredientSetHash));
        }
        return recipes;
    }

    private String rejectReason(AiRecipeDraft draft) {
        if (draft == null) {
            return "빈 초안";
        }
        if (!validator.validate(draft).isEmpty()) {
            return "형식 오류";
        }
        BigDecimal servings = BigDecimal.valueOf(draft.servings());
        boolean unrealistic = draft.ingredients().stream().anyMatch(i -> i.quantity().divide(servings, 2, RoundingMode.HALF_UP)
                .compareTo(MAX_PER_SERVING.getOrDefault(i.unit().strip().toLowerCase(Locale.ROOT), DEFAULT_MAX_PER_SERVING)) > 0);
        return unrealistic ? "비현실적인 분량" : null;
    }

    /** 재료를 표준 재료로 매핑한다. 매칭 안 된 재료가 있거나 두 재료가 같은 표준 재료로 겹치면 null. */
    private List<Ingredient> matchIngredients(AiRecipeDraft draft) {
        IngredientNormalizationResult result = normalizationService.normalize(
                draft.ingredients().stream().map(AiRecipeDraft.Item::name).toList());
        // 매칭 실패·중복이 없으면 matched는 입력 순서 그대로 초안 재료와 1:1로 대응한다.
        boolean exact = result.unmatched().isEmpty() && result.matched().size() == draft.ingredients().size();
        return exact ? result.matched() : null;
    }

    private static Recipe toRecipe(AiRecipeDraft draft, List<Ingredient> ingredients, String ingredientSetHash) {
        List<AiRecipeDraft.Item> items = draft.ingredients();
        return Recipe.builder()
                .source(RecipeSource.AI_GENERATED)
                .ingredientSetHash(ingredientSetHash)
                .title(draft.title())
                .difficulty(draft.difficulty())
                .cookTimeMinutes(draft.cookTimeMinutes())
                .servings(draft.servings())
                .ingredients(IntStream.range(0, items.size())
                        .mapToObj(i -> new RecipeIngredient(ingredients.get(i),
                                // DB는 소수 둘째 자리까지 저장한다. 반올림해 0이 되는 극소량은 최소값으로 올린다.
                                items.get(i).quantity().setScale(2, RoundingMode.HALF_UP).max(new BigDecimal("0.01")),
                                items.get(i).unit()))
                        .toList())
                .steps(draft.steps().stream().map(s -> new RecipeStep(s, null)).toList())
                .build();
    }
}
