package com.whattocook.recipe.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.global.config.JpaConfig;
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
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
class RecipeRepositoryTest {

    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager em;

    private User author;
    private Ingredient greenOnion;
    private Ingredient egg;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User("chef@example.com", "hashed", "요리사"));
        greenOnion = ingredientRepository.save(Ingredient.builder()
                .name("대파").category(IngredientCategory.VEGETABLE).baseUnit("g").build());
        egg = ingredientRepository.save(Ingredient.builder()
                .name("달걀").category(IngredientCategory.DAIRY_EGG).baseUnit("개").build());
    }

    @Test
    @DisplayName("재료·단계·태그를 순서대로 저장하고 다시 읽는다")
    void saveWithChildren() {
        List<Tag> tags = tagRepository.findAll().stream().filter(t -> t.getType() == TagType.CUISINE).limit(2).toList();
        Recipe saved = recipeRepository.saveAndFlush(recipe("파달걀볶음",
                List.of(new RecipeIngredient(egg, new BigDecimal("2"), "개"), new RecipeIngredient(greenOnion, null, null)),
                List.of(new RecipeStep("파를 썬다", null), new RecipeStep("달걀과 볶는다", "https://img.example.com/2.jpg")),
                tags));
        em.clear();

        Recipe found = recipeRepository.findByIdAndStatus(saved.getId(), RecipeStatus.PUBLISHED).orElseThrow();
        assertThat(found.getIngredients()).extracting(i -> i.getIngredient().getName()).containsExactly("달걀", "대파");
        assertThat(found.getIngredients().get(0).getQuantity()).isEqualByComparingTo("2");
        assertThat(found.getSteps()).extracting(RecipeStep::getContent).containsExactly("파를 썬다", "달걀과 볶는다");
        assertThat(found.getTags()).hasSize(2);
        assertThat(found.getSource()).isEqualTo(RecipeSource.COMMUNITY);
        assertThat(found.getLikeCount()).isZero();
        assertThat(found.getAuthor().getId()).isEqualTo(author.getId());
    }

    @Test
    @DisplayName("재료 순서를 바꾸거나 단계를 줄여도 그대로 반영된다")
    void replaceChildren() {
        Recipe saved = recipeRepository.saveAndFlush(recipe("파달걀볶음",
                List.of(new RecipeIngredient(egg, null, null), new RecipeIngredient(greenOnion, null, null)),
                List.of(new RecipeStep("1", null), new RecipeStep("2", null), new RecipeStep("3", null)),
                List.of()));
        em.clear();

        Recipe found = recipeRepository.findById(saved.getId()).orElseThrow();
        found.replaceIngredients(List.of(new RecipeIngredient(em.getReference(Ingredient.class, greenOnion.getId()), null, null),
                new RecipeIngredient(em.getReference(Ingredient.class, egg.getId()), null, null)));
        found.replaceSteps(List.of(new RecipeStep("하나뿐", null)));
        em.flush();
        em.clear();

        Recipe reloaded = recipeRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getIngredients()).extracting(i -> i.getIngredient().getName()).containsExactly("대파", "달걀");
        assertThat(reloaded.getSteps()).extracting(RecipeStep::getContent).containsExactly("하나뿐");
    }

    @Test
    @DisplayName("커서보다 작은 id를 최신순으로 조회하고, 게시 상태가 아닌 레시피는 뺀다")
    void cursorPage() {
        Recipe first = recipeRepository.save(recipe("1", List.of(), List.of(), List.of()));
        Recipe deleted = recipeRepository.save(recipe("2", List.of(), List.of(), List.of()));
        Recipe third = recipeRepository.save(recipe("3", List.of(), List.of(), List.of()));
        Recipe fourth = recipeRepository.save(recipe("4", List.of(), List.of(), List.of()));
        deleted.delete();
        recipeRepository.flush();

        assertThat(recipeRepository.findByStatusAndIdLessThanOrderByIdDesc(
                RecipeStatus.PUBLISHED, Long.MAX_VALUE, Limit.of(2)))
                .extracting(Recipe::getId).containsExactly(fourth.getId(), third.getId());
        assertThat(recipeRepository.findByStatusAndIdLessThanOrderByIdDesc(
                RecipeStatus.PUBLISHED, third.getId(), Limit.of(2)))
                .extracting(Recipe::getId).containsExactly(first.getId());
        assertThat(recipeRepository.findByIdAndStatus(deleted.getId(), RecipeStatus.PUBLISHED)).isEmpty();
    }

    @Test
    @DisplayName("작성자별 커서 조회는 내 게시 레시피만 최신순으로 주고 작성자를 함께 가져온다")
    void cursorPageByAuthor() {
        User other = userRepository.save(new User("other@example.com", "hashed", "남"));
        Recipe mine = recipeRepository.save(recipe("내 것", List.of(), List.of(), List.of()));
        Recipe hidden = recipeRepository.save(recipe("숨김", List.of(), List.of(), List.of()));
        recipeRepository.save(Recipe.builder().author(other).source(RecipeSource.COMMUNITY).title("남의 것")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1).build());
        Recipe latest = recipeRepository.save(recipe("최신", List.of(), List.of(), List.of()));
        hidden.hide();
        em.flush();
        em.clear();

        List<Recipe> page = recipeRepository.findByAuthorIdAndStatusAndIdLessThanOrderByIdDesc(
                author.getId(), RecipeStatus.PUBLISHED, Long.MAX_VALUE, Limit.of(10));

        assertThat(page).extracting(Recipe::getId).containsExactly(latest.getId(), mine.getId());
        assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(page.get(0).getAuthor())).isTrue();
        assertThat(recipeRepository.findByAuthorIdAndStatusAndIdLessThanOrderByIdDesc(
                author.getId(), RecipeStatus.PUBLISHED, latest.getId(), Limit.of(10)))
                .extracting(Recipe::getId).containsExactly(mine.getId());
    }

    @Test
    @DisplayName("마이그레이션으로 종류별 기본 태그가 들어 있다")
    void seededTags() {
        assertThat(tagRepository.findAll()).extracting(Tag::getType)
                .contains(TagType.CUISINE, TagType.TASTE, TagType.PURPOSE, TagType.DIFFICULTY);
    }

    private Recipe recipe(String title, List<RecipeIngredient> ingredients, List<RecipeStep> steps, List<Tag> tags) {
        return Recipe.builder()
                .author(author).source(RecipeSource.COMMUNITY).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(ingredients).steps(steps).tags(tags)
                .build();
    }
}
