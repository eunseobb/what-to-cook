package com.whattocook.ingredient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngredientNormalizationServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private IngredientNormalizationService normalizationService;

    private final Ingredient greenOnion = Ingredient.builder()
            .name("대파").category(IngredientCategory.VEGETABLE).baseUnit("g").aliases(List.of("파")).build();
    private final Ingredient onion = Ingredient.builder()
            .name("양파").category(IngredientCategory.VEGETABLE).baseUnit("g").build();

    @Test
    @DisplayName("별칭·공백·대소문자 차이를 표준 재료로 매핑하고, 같은 재료는 한 번만 담는다")
    void mapsAliasesToStandardIngredient() {
        given(ingredientRepository.findAllByAliasIn(anyCollection())).willReturn(List.of(greenOnion, onion));

        IngredientNormalizationResult result = normalizationService.normalize(List.of(" 파", "대 파", "양파"));

        assertThat(result.matched()).containsExactly(greenOnion, onion);
        assertThat(result.unmatched()).isEmpty();
    }

    @Test
    @DisplayName("매칭되지 않은 입력은 원문(공백 정리) 그대로 unmatched로 돌려준다")
    void collectsUnmatchedInputs() {
        given(ingredientRepository.findAllByAliasIn(anyCollection())).willReturn(List.of(greenOnion));

        IngredientNormalizationResult result = normalizationService.normalize(List.of("파", " 고수 ", "고수"));

        assertThat(result.matched()).containsExactly(greenOnion);
        assertThat(result.unmatched()).containsExactly("고수");
    }

    @Test
    @DisplayName("빈 입력은 DB를 조회하지 않는다")
    void blankInputsSkipQuery() {
        IngredientNormalizationResult result = normalizationService.normalize(Arrays.asList("", "  ", null));

        assertThat(result.matched()).isEmpty();
        assertThat(result.unmatched()).isEmpty();
        verifyNoInteractions(ingredientRepository);
    }

    @Test
    @DisplayName("표준 이름의 정규화 형태는 항상 별칭에 포함된다")
    void nameIsAlwaysAnAlias() {
        Ingredient oil = Ingredient.builder()
                .name("Olive Oil").category(IngredientCategory.SEASONING).baseUnit("ml").build();

        assertThat(oil.getName()).isEqualTo("Olive Oil");
        assertThat(oil.getAliases()).containsExactly("oliveoil");
    }
}
