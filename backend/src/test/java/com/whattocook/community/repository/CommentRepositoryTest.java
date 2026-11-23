package com.whattocook.community.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.community.domain.Comment;
import com.whattocook.global.config.JpaConfig;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.Hibernate;
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
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager em;

    private User user;
    private Recipe recipe;
    private Recipe otherRecipe;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("commenter@example.com", "hashed", "댓글러"));
        recipe = recipeRepository.save(recipe("파달걀볶음"));
        otherRecipe = recipeRepository.save(recipe("다른 레시피"));
    }

    @Test
    @DisplayName("최상위 댓글을 커서 이후부터 오래된 순으로 조회하고, 대댓글만 없는 삭제 댓글은 뺀다")
    void rootPage() {
        Comment first = save(recipe, null, "1");
        Comment deletedWithReply = save(recipe, null, "2");
        save(recipe, deletedWithReply, "2-1");
        Comment deletedAlone = save(recipe, null, "3");
        Comment deletedWithDeletedReply = save(recipe, null, "4");
        Comment deadReply = save(recipe, deletedWithDeletedReply, "4-1");
        Comment last = save(recipe, null, "5");
        save(otherRecipe, null, "다른 레시피 댓글");
        deletedWithReply.delete();
        deletedAlone.delete();
        deletedWithDeletedReply.delete();
        deadReply.delete();
        em.flush();
        em.clear();

        List<Comment> page = commentRepository.findRootPage(recipe.getId(), 0L, Limit.of(10));

        assertThat(page).extracting(Comment::getId).containsExactly(first.getId(), deletedWithReply.getId(), last.getId());
        assertThat(Hibernate.isInitialized(page.get(0).getUser())).isTrue();
        assertThat(commentRepository.findRootPage(recipe.getId(), first.getId(), Limit.of(1)))
                .extracting(Comment::getId).containsExactly(deletedWithReply.getId());
    }

    @Test
    @DisplayName("여러 댓글의 살아 있는 대댓글을 작성자와 함께 한 번에 조회한다")
    void replies() {
        Comment a = save(recipe, null, "a");
        Comment b = save(recipe, null, "b");
        Comment a1 = save(recipe, a, "a1");
        Comment b1 = save(recipe, b, "b1");
        Comment a2 = save(recipe, a, "a2");
        save(recipe, b, "b2").delete();
        em.flush();
        em.clear();

        List<Comment> replies = commentRepository.findRepliesByParentIds(List.of(a.getId(), b.getId()));

        assertThat(replies).extracting(Comment::getId).containsExactly(a1.getId(), b1.getId(), a2.getId());
        assertThat(replies).allMatch(r -> Hibernate.isInitialized(r.getUser()));
    }

    private Comment save(Recipe target, Comment parent, String content) {
        return commentRepository.save(new Comment(target, user, parent, content));
    }

    private Recipe recipe(String title) {
        return Recipe.builder()
                .source(RecipeSource.COMMUNITY).title(title)
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .build();
    }
}
