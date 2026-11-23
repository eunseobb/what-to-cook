package com.whattocook.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.community.repository.RecipeLikeRepository;
import com.whattocook.community.service.RecipeLikeService;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStep;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 두 좋아요 구현(Redis 원자 연산 / 비관적 락)이 동시 요청에서도 정확한 카운트를 남기는지 같은 시나리오로 검증한다.
 */
abstract class RecipeLikeConcurrencyTestSupport {

    private static final int USERS = 50;
    private static final int THREADS = 16;

    @Autowired
    protected RecipeLikeService recipeLikeService;
    @Autowired
    protected RecipeLikeRepository recipeLikeRepository;
    @Autowired
    protected RecipeRepository recipeRepository;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected IngredientRepository ingredientRepository;

    protected final List<User> users = new ArrayList<>();
    protected Ingredient egg;
    protected Recipe recipe;

    /** DB의 like_count가 최종값이 되도록 한다. Redis 방식은 여기서 증감분을 반영한다. */
    protected abstract void syncLikeCount();

    @BeforeEach
    void setUpLikeFixture() {
        for (int i = 0; i < USERS; i++) {
            users.add(userRepository.save(new User("liker" + i + "@example.com", "hashed", "좋아요" + i)));
        }
        egg = ingredientRepository.save(Ingredient.builder()
                .name("달걀").category(IngredientCategory.DAIRY_EGG).baseUnit("개").build());
        recipe = recipeRepository.save(Recipe.builder()
                .author(users.get(0)).source(RecipeSource.COMMUNITY).title("달걀말이")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(List.of(new RecipeIngredient(egg, null, null)))
                .steps(List.of(new RecipeStep("말아 굽는다", null)))
                .build());
    }

    @AfterEach
    void tearDownLikeFixture() {
        // 다른 통합 테스트와 같은 DB를 쓰므로 만든 데이터만 FK 순서대로 지운다.
        recipeLikeRepository.deleteAll();
        recipeRepository.delete(recipe);
        ingredientRepository.delete(egg);
        userRepository.deleteAll(users);
        users.clear();
    }

    @Test
    @DisplayName("여러 사용자가 동시에 좋아요하면 카운트가 사용자 수와 정확히 같다")
    void concurrentLikesFromManyUsers() throws InterruptedException {
        List<Throwable> errors = runConcurrently(USERS, i -> recipeLikeService.like(users.get(i).getId(), recipe.getId()));
        syncLikeCount();

        assertThat(errors).isEmpty();
        assertThat(likeCount()).isEqualTo(USERS);
        assertThat(recipeLikeRepository.count()).isEqualTo(USERS);
    }

    @Test
    @DisplayName("같은 사용자의 동시 좋아요는 한 번만 성공하고 나머지는 L001이다")
    void concurrentLikesFromSameUser() throws InterruptedException {
        Long userId = users.get(0).getId();
        List<Throwable> errors = runConcurrently(10, i -> recipeLikeService.like(userId, recipe.getId()));
        syncLikeCount();

        assertThat(errors).hasSize(9).allSatisfy(e -> assertThat(e)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_LIKED));
        assertThat(likeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요와 취소가 동시에 섞여도 카운트가 남은 좋아요 수와 같다")
    void concurrentLikesAndUnlikes() throws InterruptedException {
        int half = USERS / 2;
        assertThat(runConcurrently(half, i -> recipeLikeService.like(users.get(i).getId(), recipe.getId()))).isEmpty();
        // 앞 절반은 취소, 뒤 절반은 새로 좋아요
        List<Throwable> errors = runConcurrently(USERS, i -> {
            if (i < half) {
                recipeLikeService.unlike(users.get(i).getId(), recipe.getId());
            } else {
                recipeLikeService.like(users.get(i).getId(), recipe.getId());
            }
        });
        syncLikeCount();

        assertThat(errors).isEmpty();
        assertThat(likeCount()).isEqualTo(USERS - half);
        assertThat(recipeLikeRepository.count()).isEqualTo(USERS - half);
    }

    protected int likeCount() {
        return recipeRepository.findById(recipe.getId()).orElseThrow().getLikeCount();
    }

    /** task(0..count-1)를 동시에 출발시키고 발생한 예외를 모아 돌려준다. */
    private static List<Throwable> runConcurrently(int count, IntConsumer task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    start.await();
                    task.accept(index);
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        return List.copyOf(errors);
    }
}
