package com.whattocook.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.whattocook.ai.client.AiRecipeClient;
import com.whattocook.ai.client.AiRecipeDraft;
import com.whattocook.ai.dto.AiRecipeJobStatus;
import com.whattocook.ai.dto.AiRecommendationResponse;
import com.whattocook.ai.service.AiRecipeJobStore.Job;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.service.IngredientNormalizationResult;
import com.whattocook.ingredient.service.IngredientNormalizationService;
import com.whattocook.recipe.domain.Difficulty;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeIngredient;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.domain.ExclusionType;
import com.whattocook.user.domain.User;
import com.whattocook.user.domain.UserExcludedIngredient;
import com.whattocook.user.repository.UserExcludedIngredientRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiRecipeServiceTest {

    @Mock
    private IngredientNormalizationService normalizationService;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private UserExcludedIngredientRepository excludedIngredientRepository;
    @Mock
    private AiRecipeClient aiRecipeClient;
    @Mock
    private AiRecipeDraftValidator draftValidator;
    @Mock
    private AiRecipeJobStore jobStore;
    @Mock
    private AiRateLimiter rateLimiter;

    /** 제출된 작업을 바로 실행하지 않고 모아 두었다가 테스트에서 실행한다. */
    private final List<Runnable> submitted = new ArrayList<>();
    private AiRecipeService service;

    private final User user = withId(new User("me@example.com", "hashed", "나"), 1L);
    private final Ingredient egg = withId(ingredient("달걀"), 10L);
    private final Ingredient peanut = withId(ingredient("땅콩"), 11L);
    private final UUID jobId = UUID.randomUUID();
    private final String hash = IngredientSetHash.of(List.of(10L));

    @BeforeEach
    void setUp() {
        service = new AiRecipeService(normalizationService, recipeRepository, excludedIngredientRepository,
                aiRecipeClient, draftValidator, jobStore, rateLimiter, submitted::add);
    }

    private void givenOwnedEgg() {
        given(normalizationService.normalize(List.of("계란")))
                .willReturn(new IngredientNormalizationResult(List.of(egg), List.of()));
    }

    @Test
    @DisplayName("같은 재료 집합의 AI 레시피가 있으면 LLM을 부르지 않고 재사용하며, 알레르기 재료 레시피는 뺀다")
    void cacheHit() {
        givenOwnedEgg();
        Recipe reused = aiRecipe(100L, egg);
        Recipe allergic = aiRecipe(101L, egg, peanut);
        given(recipeRepository.findAllByIngredientSetHashAndSourceAndStatusOrderByIdAsc(
                hash, RecipeSource.AI_GENERATED, RecipeStatus.PUBLISHED)).willReturn(List.of(allergic, reused));
        given(jobStore.create(any())).willReturn(jobId);
        given(excludedIngredientRepository.findAllByUserIdOrderByIngredientIdAsc(1L))
                .willReturn(List.of(new UserExcludedIngredient(user, peanut, ExclusionType.ALLERGY)));

        AiRecommendationResponse response = service.request(1L, List.of("계란"));

        assertThat(response.status()).isEqualTo(AiRecipeJobStatus.COMPLETED);
        assertThat(response.recipes()).extracting(r -> r.id()).containsExactly(100L);
        assertThat(response.recipes().get(0).source()).isEqualTo(RecipeSource.AI_GENERATED);
        verify(jobStore).create(new Job(1L, AiRecipeJobStatus.COMPLETED, List.of(101L, 100L)));
        assertThat(submitted).isEmpty();
        verify(rateLimiter, never()).tryAcquire(any());
    }

    @Test
    @DisplayName("캐시 미스면 PENDING으로 응답하고, 전용 스레드 풀에서 생성·검증·저장 후 COMPLETED로 바꾼다")
    void cacheMiss() {
        givenOwnedEgg();
        given(recipeRepository.findAllByIngredientSetHashAndSourceAndStatusOrderByIdAsc(any(), any(), any()))
                .willReturn(List.of());
        given(rateLimiter.tryAcquire(1L)).willReturn(true);
        given(jobStore.create(new Job(1L, AiRecipeJobStatus.PENDING, List.of()))).willReturn(jobId);

        AiRecommendationResponse response = service.request(1L, List.of("계란"));

        assertThat(response).isEqualTo(AiRecommendationResponse.pending(jobId));
        verify(aiRecipeClient, never()).generate(anyList());

        List<AiRecipeDraft> drafts = List.of(mock(AiRecipeDraft.class));
        Recipe generated = aiRecipe(null, egg);
        given(aiRecipeClient.generate(List.of("달걀"))).willReturn(drafts);
        given(draftValidator.toRecipes(drafts, List.of(10L), hash)).willReturn(List.of(generated));
        given(recipeRepository.saveAll(List.of(generated))).willAnswer(inv -> {
            ReflectionTestUtils.setField(generated, "id", 200L);
            return List.of(generated);
        });
        submitted.forEach(Runnable::run);

        verify(jobStore).save(jobId, new Job(1L, AiRecipeJobStatus.COMPLETED, List.of(200L)));
    }

    @Test
    @DisplayName("LLM 호출이 실패하거나 검증을 통과한 레시피가 없으면 원인 코드와 함께 작업을 FAILED로 남긴다")
    void generateFails() {
        given(aiRecipeClient.generate(List.of("달걀"))).willThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));
        service.generate(jobId, 1L, List.of(egg), hash);
        verify(jobStore).save(jobId, Job.failed(1L, ErrorCode.AI_UNAVAILABLE));

        willThrow(new IllegalStateException("boom")).given(aiRecipeClient).generate(List.of("달걀"));
        service.generate(jobId, 1L, List.of(egg), hash);

        willReturn(List.of()).given(aiRecipeClient).generate(List.of("달걀"));
        given(draftValidator.toRecipes(List.of(), List.of(10L), hash)).willReturn(List.of());
        given(recipeRepository.saveAll(List.of())).willReturn(List.of());
        service.generate(jobId, 1L, List.of(egg), hash);
        verify(jobStore, times(2)).save(jobId, Job.failed(1L, ErrorCode.AI_GENERATION_FAILED));
    }

    @Test
    @DisplayName("스레드 풀이 가득 차면 작업을 FAILED로 남기고 429로 거절한다")
    void rejected() {
        service = new AiRecipeService(normalizationService, recipeRepository, excludedIngredientRepository,
                aiRecipeClient, draftValidator, jobStore, rateLimiter, task -> {
            throw new RejectedExecutionException();
        });
        given(recipeRepository.findAllByIngredientSetHashAndSourceAndStatusOrderByIdAsc(any(), any(), any()))
                .willReturn(List.of());
        given(rateLimiter.tryAcquire(1L)).willReturn(true);
        given(jobStore.create(any())).willReturn(jobId);
        givenOwnedEgg();

        assertThatThrownBy(() -> service.request(1L, List.of("계란")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        verify(jobStore).save(jobId, Job.failed(1L, ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    @DisplayName("캐시 미스에서 사용자별 LLM 호출 한도를 넘으면 작업을 만들지 않고 429(AI006)로 거절한다")
    void rateLimited() {
        givenOwnedEgg();
        given(recipeRepository.findAllByIngredientSetHashAndSourceAndStatusOrderByIdAsc(any(), any(), any()))
                .willReturn(List.of());
        given(rateLimiter.tryAcquire(1L)).willReturn(false);

        assertThatThrownBy(() -> service.request(1L, List.of("계란")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AI_RATE_LIMITED);
        verify(jobStore, never()).create(any());
        assertThat(submitted).isEmpty();
    }

    @Test
    @DisplayName("재료가 하나도 매칭되지 않으면 거절한다")
    void noIngredient() {
        given(normalizationService.normalize(List.of("트러플")))
                .willReturn(new IngredientNormalizationResult(List.of(), List.of("트러플")));

        assertThatThrownBy(() -> service.request(1L, List.of("트러플")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AI_INGREDIENT_NOT_FOUND);
    }

    @Test
    @DisplayName("작업은 요청한 사용자만 조회하고, 완료되면 게시 중인 레시피만 준다")
    void getJob() {
        Recipe done = aiRecipe(100L, egg);
        Recipe deleted = aiRecipe(101L, egg);
        deleted.delete();
        given(jobStore.find(jobId)).willReturn(Optional.of(new Job(1L, AiRecipeJobStatus.COMPLETED, List.of(100L, 101L))));
        given(recipeRepository.findAllById(List.of(100L, 101L))).willReturn(List.of(deleted, done));
        given(excludedIngredientRepository.findAllByUserIdOrderByIngredientIdAsc(1L)).willReturn(List.of());

        assertThat(service.getJob(1L, jobId).recipes()).extracting(r -> r.id()).containsExactly(100L);
        assertThatThrownBy(() -> service.getJob(2L, jobId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AI_JOB_NOT_FOUND);

        UUID pending = UUID.randomUUID();
        given(jobStore.find(eq(pending))).willReturn(Optional.of(new Job(1L, AiRecipeJobStatus.PENDING, List.of())));
        assertThat(service.getJob(1L, pending)).isEqualTo(AiRecommendationResponse.pending(pending));

        UUID failed = UUID.randomUUID();
        given(jobStore.find(eq(failed))).willReturn(Optional.of(Job.failed(1L, ErrorCode.AI_TIMEOUT)));
        assertThat(service.getJob(1L, failed)).satisfies(r -> {
            assertThat(r.status()).isEqualTo(AiRecipeJobStatus.FAILED);
            assertThat(r.errorCode()).isEqualTo("AI004");
            assertThat(r.recipes()).isNull();
        });
    }

    private static Ingredient ingredient(String name) {
        return Ingredient.builder().name(name).category(IngredientCategory.ETC).baseUnit("g").build();
    }

    private static Recipe aiRecipe(Long id, Ingredient... ingredients) {
        Recipe recipe = Recipe.builder().source(RecipeSource.AI_GENERATED).title("AI 레시피")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1)
                .ingredients(List.of(ingredients).stream().map(i -> new RecipeIngredient(i, null, null)).toList())
                .build();
        return id == null ? recipe : withId(recipe, id);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
