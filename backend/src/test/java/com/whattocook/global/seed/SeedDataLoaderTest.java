package com.whattocook.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.global.config.JpaConfig;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.price.domain.IngredientPrice;
import com.whattocook.price.domain.PriceSource;
import com.whattocook.price.repository.IngredientPriceRepository;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recipe.repository.TagRepository;
import com.whattocook.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
class SeedDataLoaderTest {

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private IngredientPriceRepository ingredientPriceRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("시드가 적재되고, 레시피 재료는 마스터 재료와 그 재료의 단위(기준 단위 또는 환산 단위)를 쓴다")
    void loadsSeedReferencingValidIngredients() throws Exception {
        loader().run(null);
        em.flush();
        em.clear();

        assertThat(ingredientRepository.count()).isGreaterThanOrEqualTo(150);
        List<Recipe> recipes = recipeRepository.findAll();
        assertThat(recipes).hasSizeGreaterThanOrEqualTo(300);
        assertThat(recipes).allSatisfy(recipe -> {
            assertThat(recipe.getSource()).isEqualTo(RecipeSource.COMMUNITY);
            assertThat(recipe.getIngredients()).isNotEmpty();
            assertThat(recipe.getSteps()).isNotEmpty();
            assertThat(recipe.getTags()).isNotEmpty();
            for (RecipeIngredient ri : recipe.getIngredients()) {
                Ingredient ingredient = ri.getIngredient();
                if (ri.getUnit() != null) {
                    assertThat(ri.getUnit().equals(ingredient.getBaseUnit())
                            || ingredient.getUnitConversions().containsKey(ri.getUnit()))
                            .as("%s: %s 단위 %s", recipe.getTitle(), ingredient.getName(), ri.getUnit())
                            .isTrue();
                }
            }
        });

        List<IngredientPrice> prices = ingredientPriceRepository.findAll();
        assertThat(prices).hasSizeGreaterThanOrEqualTo(150);
        assertThat(prices).extracting(IngredientPrice::getSource).contains(PriceSource.values());
        assertThat(prices).allSatisfy(p -> {
            assertThat(p.getPricePerBaseUnit()).isPositive();
            assertThat(p.getReferenceDate()).isNotNull();
        });
    }

    @Test
    @DisplayName("다시 실행해도 이미 데이터가 있는 테이블에는 적재하지 않는다")
    void secondRunDoesNotDuplicate() throws Exception {
        loader().run(null);
        long ingredients = ingredientRepository.count();
        long recipes = recipeRepository.count();
        long prices = ingredientPriceRepository.count();

        loader().run(null);

        assertThat(ingredientRepository.count()).isEqualTo(ingredients);
        assertThat(recipeRepository.count()).isEqualTo(recipes);
        assertThat(ingredientPriceRepository.count()).isEqualTo(prices);
    }

    private SeedDataLoader loader() {
        return new SeedDataLoader(ingredientRepository, recipeRepository, tagRepository, ingredientPriceRepository);
    }
}
