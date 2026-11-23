package com.whattocook.community.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.community.domain.RecipeBookmark;
import com.whattocook.global.config.JpaConfig;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import java.util.List;
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
class RecipeBookmarkRepositoryTest {

    @Autowired
    private RecipeBookmarkRepository recipeBookmarkRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private UserRepository userRepository;

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
}
