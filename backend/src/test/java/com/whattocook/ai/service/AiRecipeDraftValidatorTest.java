package com.whattocook.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

import com.whattocook.ai.client.AiRecipeDraft;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.service.IngredientNormalizationResult;
import com.whattocook.ingredient.service.IngredientNormalizationService;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiRecipeDraftValidatorTest {

    @Mock
    private IngredientNormalizationService normalizationService;

    private AiRecipeDraftValidator validator;

    private final Ingredient egg = ingredient("달걀", 1L);
    private final Ingredient rice = ingredient("밥", 2L);
    private final Ingredient salt = ingredient("소금", 3L);
    private final Map<String, Ingredient> master = Map.of("달걀", egg, "밥", rice, "소금", salt);

    @BeforeEach
    void setUp() {
        validator = new AiRecipeDraftValidator(Validation.buildDefaultValidatorFactory().getValidator(),
                normalizationService);
        // 실제 정규화 서비스처럼 입력 순서대로, 중복은 한 번만 매칭한다.
        given(normalizationService.normalize(anyCollection())).willAnswer(inv -> {
            List<String> names = List.copyOf(inv.<Collection<String>>getArgument(0));
            List<String> distinct = names.stream().distinct().toList();
            return new IngredientNormalizationResult(
                    distinct.stream().filter(master::containsKey).map(master::get).toList(),
                    distinct.stream().filter(n -> !master.containsKey(n)).toList());
        });
    }

    @Test
    @DisplayName("통과한 초안을 AI_GENERATED 레시피로 바꾸고 해시·표준 재료·분량을 채운다")
    void valid() {
        List<Recipe> recipes = validator.toRecipes(
                List.of(draft("달걀밥", 1, item("달걀", "2", "개"), item("밥", "200.555", "g"), item("소금", "0.001", "꼬집"))),
                List.of(1L, 2L), "hash");

        assertThat(recipes).singleElement().satisfies(r -> {
            assertThat(r.getSource()).isEqualTo(RecipeSource.AI_GENERATED);
            assertThat(r.getIngredientSetHash()).isEqualTo("hash");
            assertThat(r.getAuthor()).isNull();
            assertThat(r.getIngredients()).extracting(i -> i.getIngredient().getId()).containsExactly(1L, 2L, 3L);
            assertThat(r.getIngredients()).extracting(i -> i.getQuantity().toPlainString())
                    .containsExactly("2.00", "200.56", "0.01");
            assertThat(r.getSteps()).hasSize(1);
        });
    }

    @Test
    @DisplayName("마스터에 없는 재료·중복 재료·형식 오류·비현실적 분량·사용자 재료 미사용 초안은 버린다")
    void rejects() {
        List<Recipe> recipes = validator.toRecipes(List.of(
                draft("마스터에 없음", 1, item("달걀", "1", "개"), item("트러플", "1", "g")),
                draft("중복", 1, item("달걀", "1", "개"), item("달걀", "2", "개")),
                draft(" ", 1, item("달걀", "1", "개")),
                draft("너무 많음", 2, item("밥", "2001", "g")),
                draft("너무 많은 개수", 1, item("달걀", "11", "개")),
                draft("사용자 재료 없음", 1, item("소금", "1", "꼬집")),
                draft("통과", 2, item("밥", "2000", "g"))), List.of(1L, 2L), "hash");

        assertThat(recipes).extracting(Recipe::getTitle).containsExactly("통과");
    }

    @Test
    @DisplayName("최대 3개까지만 받는다")
    void maxThree() {
        AiRecipeDraft d = draft("달걀", 1, item("달걀", "1", "개"));

        assertThat(validator.toRecipes(List.of(d, d, d, d), List.of(1L), "hash")).hasSize(3);
    }

    private static AiRecipeDraft draft(String title, int servings, AiRecipeDraft.Item... items) {
        return new AiRecipeDraft(title, Difficulty.EASY, 10, servings, Arrays.asList(items), List.of("조리한다"));
    }

    private static AiRecipeDraft.Item item(String name, String quantity, String unit) {
        return new AiRecipeDraft.Item(name, new BigDecimal(quantity), unit);
    }

    private static Ingredient ingredient(String name, Long id) {
        Ingredient ingredient = Ingredient.builder().name(name).category(IngredientCategory.ETC).baseUnit("g").build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }
}
