package com.whattocook.ingredient.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whattocook.global.config.JpaConfig;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
class IngredientRepositoryTest {

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("이름·별칭·단위 환산을 저장하고, 별칭으로 재료를 찾는다")
    void findAllByAliasIn() {
        ingredientRepository.saveAndFlush(Ingredient.builder()
                .name("대파").category(IngredientCategory.VEGETABLE).baseUnit("g")
                .aliases(List.of("파", "대 파"))
                .unitConversions(Map.of("대", new BigDecimal("100")))
                .build());
        ingredientRepository.saveAndFlush(Ingredient.builder()
                .name("소금").category(IngredientCategory.SEASONING).baseUnit("g").pantryStaple(true)
                .build());
        em.clear();

        List<Ingredient> found = ingredientRepository.findAllByAliasIn(List.of("파", "소금", "없는재료"));

        assertThat(found).extracting(Ingredient::getName).containsExactlyInAnyOrder("대파", "소금");
        Ingredient greenOnion = found.stream().filter(i -> i.getName().equals("대파")).findFirst().orElseThrow();
        assertThat(greenOnion.getAliases()).containsExactlyInAnyOrder("대파", "파");
        assertThat(greenOnion.getUnitConversions().get("대")).isEqualByComparingTo("100");
        assertThat(greenOnion.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 별칭을 두 재료에 등록할 수 없다")
    void aliasIsUniqueAcrossIngredients() {
        ingredientRepository.saveAndFlush(Ingredient.builder()
                .name("대파").category(IngredientCategory.VEGETABLE).baseUnit("g").aliases(List.of("파")).build());

        assertThatThrownBy(() -> ingredientRepository.saveAndFlush(Ingredient.builder()
                .name("쪽파").category(IngredientCategory.VEGETABLE).baseUnit("g").aliases(List.of("파")).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
