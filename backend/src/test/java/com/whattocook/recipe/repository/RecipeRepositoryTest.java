package com.whattocook.recipe.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.global.config.JpaConfig;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSort;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.domain.RecipeStep;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;
import com.whattocook.recipe.dto.RecipeSummaryResponse;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
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

        RecipeListCondition latest = new RecipeListCondition(List.of(), RecipeSort.LATEST, null);
        assertThat(recipeRepository.search(latest, null, 2))
                .extracting(Recipe::getId).containsExactly(fourth.getId(), third.getId());
        assertThat(recipeRepository.search(latest, new RecipeCursor(0, null, third.getId()), 2))
                .extracting(Recipe::getId).containsExactly(first.getId());
        assertThat(recipeRepository.findByIdAndStatus(deleted.getId(), RecipeStatus.PUBLISHED)).isEmpty();
    }

    @Test
    @DisplayName("인기순은 (좋아요 수, 작성 시각, id) 내림차순이고 복합 커서로 넘겨도 중복·누락이 없다")
    void searchPopularWithCompositeCursor() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 11, 1, 12, 0);
        Recipe a = saveWith("a", 5, sameTime);
        Recipe b = saveWith("b", 5, sameTime);
        Recipe c = saveWith("c", 9, sameTime.minusDays(1));
        Recipe d = saveWith("d", 0, sameTime.plusDays(1));
        Recipe hidden = saveWith("hidden", 100, sameTime);
        hidden.hide();
        em.flush();
        em.clear();
        RecipeListCondition popular = new RecipeListCondition(List.of(), RecipeSort.POPULAR, null);

        List<Recipe> first = recipeRepository.search(popular, null, 2);
        Recipe last = first.get(1);
        List<Recipe> second = recipeRepository.search(popular,
                new RecipeCursor(last.getLikeCount(), last.getCreatedAt(), last.getId()), 10);

        assertThat(first).extracting(Recipe::getId).containsExactly(c.getId(), b.getId());
        assertThat(second).extracting(Recipe::getId).containsExactly(a.getId(), d.getId());
        assertThat(first.get(0).getAuthor().getNickname()).isEqualTo("요리사");
    }

    @Test
    @DisplayName("태그는 모두 가진 레시피만, 기간은 기준 시각 이후 작성된 레시피만 남긴다")
    void searchByTagsAndPeriod() {
        List<Tag> tags = tagRepository.findAll().stream().limit(2).toList();
        Recipe both = recipeRepository.save(recipe("both", List.of(), List.of(), tags));
        Recipe one = recipeRepository.save(recipe("one", List.of(), List.of(), tags.subList(0, 1)));
        Recipe old = recipeRepository.save(recipe("old", List.of(), List.of(), tags));
        recipeRepository.flush();
        setCreatedAt(old, LocalDateTime.now().minusDays(10));
        em.clear();
        Long t1 = tags.get(0).getId();
        Long t2 = tags.get(1).getId();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        assertThat(recipeRepository.search(new RecipeListCondition(List.of(t1, t2), RecipeSort.LATEST, null), null, 10))
                .extracting(Recipe::getId).containsExactly(old.getId(), both.getId());
        assertThat(recipeRepository.search(new RecipeListCondition(List.of(t1), RecipeSort.LATEST, weekAgo), null, 10))
                .extracting(Recipe::getId).containsExactly(one.getId(), both.getId());
        assertThat(recipeRepository.search(new RecipeListCondition(List.of(t1), RecipeSort.LATEST, null),
                new RecipeCursor(0, null, one.getId()), 10))
                .extracting(Recipe::getId).containsExactly(both.getId());
    }

    private Recipe saveWith(String title, int likeCount, LocalDateTime createdAt) {
        Recipe saved = recipeRepository.saveAndFlush(recipe(title, List.of(), List.of(), List.of()));
        recipeRepository.addLikeCount(saved.getId(), likeCount);
        setCreatedAt(saved, createdAt);
        return saved;
    }

    private void setCreatedAt(Recipe recipe, LocalDateTime createdAt) {
        em.createNativeQuery("update recipe set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt).setParameter("id", recipe.getId()).executeUpdate();
    @DisplayName("목록의 대표 태그는 레시피 수와 상관없이 일정한 쿼리 수로 가져온다(N+1 없음)")
    void listWithRepresentativeTagWithoutNPlusOne() {
        List<Tag> tags = tagRepository.findAll();
        for (int i = 0; i < 5; i++) {
            recipeRepository.save(recipe("레시피" + i, List.of(), List.of(), tags.subList(i, i + 2)));
        }
        recipeRepository.flush();
        em.clear();
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<RecipeSummaryResponse> items = recipeRepository.findByStatusAndIdLessThanOrderByIdDesc(
                RecipeStatus.PUBLISHED, Long.MAX_VALUE, Limit.of(5)).stream().map(RecipeSummaryResponse::from).toList();

        assertThat(items).hasSize(5).allSatisfy(item -> assertThat(item.representativeTag()).isNotNull());
        // 목록 1 + 작성자 1 + 태그 1(IN 배치). N+1이면 레시피 수만큼 늘어난다.
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("재료 집합 해시가 같은 게시 중인 AI 레시피만 재사용 대상으로 찾는다")
    void findAiRecipesByIngredientSetHash() {
        Recipe ai = recipeRepository.save(aiRecipe("AI 파달걀", "hash-a"));
        recipeRepository.save(aiRecipe("다른 재료", "hash-b"));
        recipeRepository.save(aiRecipe("삭제된 AI", "hash-a")).delete();
        recipeRepository.save(Recipe.builder().author(author).source(RecipeSource.COMMUNITY).ingredientSetHash("hash-a")
                .title("커뮤니티").difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1).build());
        recipeRepository.flush();

        assertThat(recipeRepository.findAllByIngredientSetHashAndSourceAndStatusOrderByIdAsc(
                "hash-a", RecipeSource.AI_GENERATED, RecipeStatus.PUBLISHED))
                .extracting(Recipe::getId).containsExactly(ai.getId());
    }

    @Test
    @DisplayName("마이그레이션으로 종류별 기본 태그가 들어 있다")
    void seededTags() {
        assertThat(tagRepository.findAll()).extracting(Tag::getType)
                .contains(TagType.CUISINE, TagType.TASTE, TagType.PURPOSE, TagType.DIFFICULTY);
        assertThat(tagRepository.findAll()).extracting(Tag::getColorKey).doesNotContainNull()
                .contains("accent-sage", "accent-navy");
    }

    private Recipe aiRecipe(String title, String hash) {
        return Recipe.builder()
                .source(RecipeSource.AI_GENERATED).ingredientSetHash(hash).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .build();
    }

    private Recipe recipe(String title, List<RecipeIngredient> ingredients, List<RecipeStep> steps, List<Tag> tags) {
        return Recipe.builder()
                .author(author).source(RecipeSource.COMMUNITY).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(ingredients).steps(steps).tags(tags)
                .build();
    }
}
