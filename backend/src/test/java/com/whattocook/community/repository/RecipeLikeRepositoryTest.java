package com.whattocook.community.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.community.domain.RecipeLike;
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
class RecipeLikeRepositoryTest {

    @Autowired
    private RecipeLikeRepository recipeLikeRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("내 좋아요 목록은 좋아요한 최신순 커서 조회이고, 삭제된 레시피와 남의 좋아요는 뺀다")
    void findPageByUserId() {
        User user = userRepository.save(new User("liker@example.com", "hashed", "좋아요러"));
        User other = userRepository.save(new User("other-liker@example.com", "hashed", "남"));
        Recipe first = saveRecipe(user, "첫째");
        Recipe deleted = saveRecipe(user, "삭제");
        Recipe latest = saveRecipe(user, "최신");
        recipeLikeRepository.save(new RecipeLike(user, first));
        recipeLikeRepository.save(new RecipeLike(user, deleted));
        recipeLikeRepository.save(new RecipeLike(other, first));
        recipeLikeRepository.save(new RecipeLike(user, latest));
        deleted.delete();
        em.flush();
        em.clear();

        List<RecipeLike> page = recipeLikeRepository.findPageByUserId(
                user.getId(), RecipeStatus.PUBLISHED, Long.MAX_VALUE, Limit.of(10));

        assertThat(page).extracting(l -> l.getRecipe().getId()).containsExactly(latest.getId(), first.getId());
        assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(page.get(0).getRecipe().getAuthor())).isTrue();
        assertThat(recipeLikeRepository.findPageByUserId(
                user.getId(), RecipeStatus.PUBLISHED, page.get(0).getId(), Limit.of(1)))
                .extracting(l -> l.getRecipe().getId()).containsExactly(first.getId());
    }

    private Recipe saveRecipe(User author, String title) {
        return recipeRepository.save(Recipe.builder()
                .author(author).source(RecipeSource.COMMUNITY).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(List.of()).steps(List.of())
                .build());
    }
}
