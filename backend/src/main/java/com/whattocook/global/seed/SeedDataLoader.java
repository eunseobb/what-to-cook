package com.whattocook.global.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.price.domain.IngredientPrice;
import com.whattocook.price.domain.PriceSource;
import com.whattocook.price.repository.IngredientPriceRepository;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStep;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.recipe.repository.TagRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콜드 스타트용 시드 데이터(재료 마스터, 커뮤니티 레시피, 재료 가격)를 local 프로파일에서만 적재한다.
 * 테이블별로 비어 있을 때만 넣으므로 재기동해도 중복되지 않고, 이미 쌓인 데이터는 건드리지 않는다.
 * 자동완성 색인은 ApplicationReadyEvent에서 다시 만들어지므로 여기서 적재한 재료도 반영된다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class SeedDataLoader implements ApplicationRunner {

    static final String INGREDIENTS = "seed/ingredients.csv";
    static final String RECIPES = "seed/recipes.json";
    static final String PRICES = "seed/ingredient-prices.csv";

    private final IngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;
    private final TagRepository tagRepository;
    private final IngredientPriceRepository ingredientPriceRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (ingredientRepository.count() == 0) {
            List<Ingredient> ingredients = readCsv(INGREDIENTS).stream().map(SeedDataLoader::toIngredient).toList();
            ingredientRepository.saveAll(ingredients);
            log.info("seed: ingredients {}", ingredients.size());
        }
        Map<String, Ingredient> ingredientByName = ingredientRepository.findAll().stream()
                .collect(Collectors.toMap(Ingredient::getName, Function.identity()));

        if (recipeRepository.count() == 0) {
            Map<String, Tag> tagByName = tagRepository.findAll().stream()
                    .collect(Collectors.toMap(Tag::getName, Function.identity()));
            List<Recipe> recipes = readRecipes().stream()
                    .map(r -> r.toRecipe(ingredientByName, tagByName)).toList();
            recipeRepository.saveAll(recipes);
            log.info("seed: recipes {}", recipes.size());
        }

        if (ingredientPriceRepository.count() == 0) {
            List<IngredientPrice> prices = readCsv(PRICES).stream()
                    .map(row -> toPrice(row, ingredientByName)).toList();
            ingredientPriceRepository.saveAll(prices);
            log.info("seed: ingredient prices {}", prices.size());
        }
    }

    /** name,category,base_unit,pantry_staple,aliases(|),unit_conversions(단위:환산량|) */
    private static Ingredient toIngredient(String[] row) {
        Map<String, BigDecimal> conversions = new HashMap<>();
        for (String pair : split(row[5])) {
            String[] kv = pair.split(":");
            conversions.put(kv[0], new BigDecimal(kv[1]));
        }
        return Ingredient.builder()
                .name(row[0])
                .category(IngredientCategory.valueOf(row[1]))
                .baseUnit(row[2])
                .pantryStaple(Boolean.parseBoolean(row[3]))
                .aliases(split(row[4]))
                .unitConversions(conversions)
                .build();
    }

    /** ingredient,source,price,package_amount,reference_date → 기준 단위당 가격 */
    private static IngredientPrice toPrice(String[] row, Map<String, Ingredient> ingredientByName) {
        BigDecimal perBaseUnit = new BigDecimal(row[2]).divide(new BigDecimal(row[3]), 4, RoundingMode.HALF_UP);
        return new IngredientPrice(find(ingredientByName, row[0], PRICES), perBaseUnit,
                LocalDate.parse(row[4]), PriceSource.valueOf(row[1]));
    }

    private static List<String> split(String value) {
        return value.isEmpty() ? List.of() : Arrays.asList(value.split("\\|"));
    }

    private static <T> T find(Map<String, T> byName, String name, String file) {
        T found = byName.get(name);
        if (found == null) {
            throw new IllegalStateException(file + ": unknown reference '" + name + "'");
        }
        return found;
    }

    /** '#' 주석 줄과 헤더 줄을 건너뛴다. 값에 쉼표가 없는 단순 CSV만 다룬다. */
    private static List<String[]> readCsv(String path) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .skip(1)
                    .map(line -> line.split(",", -1))
                    .toList();
        }
    }

    private static List<SeedRecipe> readRecipes() throws IOException {
        try (InputStream in = new ClassPathResource(RECIPES).getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<>() {
            });
        }
    }

    record SeedRecipe(String title, Difficulty difficulty, int cookTimeMinutes, int servings, List<String> tags,
                      List<SeedIngredient> ingredients, List<String> steps) {

        Recipe toRecipe(Map<String, Ingredient> ingredientByName, Map<String, Tag> tagByName) {
            return Recipe.builder()
                    .source(RecipeSource.COMMUNITY)
                    .title(title)
                    .difficulty(difficulty)
                    .cookTimeMinutes(cookTimeMinutes)
                    .servings(servings)
                    .ingredients(ingredients.stream()
                            .map(i -> new RecipeIngredient(find(ingredientByName, i.name(), RECIPES),
                                    i.quantity(), i.unit()))
                            .toList())
                    .steps(steps.stream().map(s -> new RecipeStep(s, null)).toList())
                    .tags(tags.stream().map(t -> find(tagByName, t, RECIPES)).toList())
                    .build();
        }
    }

    record SeedIngredient(String name, BigDecimal quantity, String unit) {
    }
}
