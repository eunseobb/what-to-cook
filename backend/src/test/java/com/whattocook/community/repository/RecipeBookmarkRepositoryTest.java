package com.whattocook.community.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.community.domain.RecipeBookmark;
import com.whattocook.global.config.JpaConfig;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
class RecipeBookmarkRepositoryTest {

    @Autowired
    private RecipeBookmarkRepository recipeBookmarkRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager em;

    private User user;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("bookmarker@example.com", "hashed", "저장러"));
        recipe = recipeRepository.saveAndFlush(Recipe.builder()
                .author(user).source(RecipeSource.COMMUNITY).title("파달걀볶음")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(List.of()).steps(List.of())
                .build());
    }

    @Test
    @DisplayName("같은 레시피를 두 번 저장해도 한 행만 남는다")
    void insertIfAbsentIsIdempotent() {
        assertThat(recipeBookmarkRepository.insertIfAbsent(user.getId(), recipe.getId())).isEqualTo(1);
        assertThat(recipeBookmarkRepository.insertIfAbsent(user.getId(), recipe.getId())).isZero();

        List<RecipeBookmark> bookmarks = recipeBookmarkRepository.findAll();
        assertThat(bookmarks).hasSize(1);
        assertThat(bookmarks.get(0).getUser().getId()).isEqualTo(user.getId());
        assertThat(bookmarks.get(0).getRecipe().getId()).isEqualTo(recipe.getId());
        assertThat(bookmarks.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("해제는 저장 행이 없어도 에러 없이 0건을 지운다")
    void deleteIsIdempotent() {
        recipeBookmarkRepository.insertIfAbsent(user.getId(), recipe.getId());

        assertThat(recipeBookmarkRepository.deleteByUserIdAndRecipeId(user.getId(), recipe.getId())).isEqualTo(1);
        assertThat(recipeBookmarkRepository.deleteByUserIdAndRecipeId(user.getId(), recipe.getId())).isZero();
        assertThat(recipeBookmarkRepository.count()).isZero();
    }

    @Test
    @DisplayName("내 저장 목록은 저장한 최신순 커서 조회이고, 게시 중이 아닌 레시피는 뺀다")
    void findPageByUserId() {
        Recipe hidden = saveRecipe("숨김");
        Recipe latest = saveRecipe("최신");
        recipeBookmarkRepository.insertIfAbsent(user.getId(), recipe.getId());
        recipeBookmarkRepository.insertIfAbsent(user.getId(), hidden.getId());
        recipeBookmarkRepository.insertIfAbsent(user.getId(), latest.getId());
        hidden.hide();
        em.flush();
        em.clear();

        List<RecipeBookmark> page = recipeBookmarkRepository.findPageByUserId(
                user.getId(), RecipeStatus.PUBLISHED, Long.MAX_VALUE, Limit.of(10));

        assertThat(page).extracting(b -> b.getRecipe().getId()).containsExactly(latest.getId(), recipe.getId());
        assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(page.get(0).getRecipe().getAuthor())).isTrue();
        assertThat(recipeBookmarkRepository.findPageByUserId(
                user.getId(), RecipeStatus.PUBLISHED, page.get(0).getId(), Limit.of(10)))
                .extracting(b -> b.getRecipe().getId()).containsExactly(recipe.getId());
    }

    private Recipe saveRecipe(String title) {
        return recipeRepository.saveAndFlush(Recipe.builder()
                .author(user).source(RecipeSource.COMMUNITY).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(List.of()).steps(List.of())
                .build());
    }
}
