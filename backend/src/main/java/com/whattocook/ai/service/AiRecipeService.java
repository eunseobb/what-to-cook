package com.whattocook.ai.service;

import com.whattocook.ai.client.AiRecipeClient;
import com.whattocook.ai.client.AiRecipeDraft;
import com.whattocook.ai.dto.AiRecipeJobStatus;
import com.whattocook.ai.dto.AiRecommendationResponse;
import com.whattocook.ai.service.AiRecipeJobStore.Job;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.service.IngredientNormalizationService;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSource;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.dto.RecipeDetailResponse;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.domain.ExclusionType;
import com.whattocook.user.repository.UserExcludedIngredientRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM 기타 추천. 재료 집합 해시가 같은 AI 레시피가 있으면 재사용하고(캐시 히트), 없으면 전용 스레드 풀에서
 * 생성·검증·저장한다. 생성은 비동기라 요청 시 jobId를 주고, 클라이언트는 결과를 폴링한다.
 * 알레르기 재료가 든 레시피는 응답에서 뺀다(같은 해시의 레시피를 여러 사용자가 공유하므로 조회 시점에 거른다).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AiRecipeService {

    private final IngredientNormalizationService normalizationService;
    private final RecipeRepository recipeRepository;
    private final UserExcludedIngredientRepository excludedIngredientRepository;
    private final AiRecipeClient aiRecipeClient;
    private final AiRecipeDraftValidator draftValidator;
    private final AiRecipeJobStore jobStore;
    private final Executor executor;

    public AiRecipeService(IngredientNormalizationService normalizationService, RecipeRepository recipeRepository,
                           UserExcludedIngredientRepository excludedIngredientRepository, AiRecipeClient aiRecipeClient,
                           AiRecipeDraftValidator draftValidator, AiRecipeJobStore jobStore,
                           @Qualifier(AiRecipeExecutorConfig.EXECUTOR) Executor executor) {
        this.normalizationService = normalizationService;
        this.recipeRepository = recipeRepository;
        this.excludedIngredientRepository = excludedIngredientRepository;
        this.aiRecipeClient = aiRecipeClient;
        this.draftValidator = draftValidator;
        this.jobStore = jobStore;
        this.executor = executor;
    }

    public AiRecommendationResponse request(Long userId, List<String> inputs) {
        List<Ingredient> owned = normalizationService.normalize(inputs).matched();
        if (owned.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_INGREDIENT_NOT_FOUND);
        }
        String hash = IngredientSetHash.of(owned.stream().map(Ingredient::getId).toList());

        List<Recipe> reused = recipeRepository.findAllByIngredientSetHashAndSourceAndStatusOrderByIdAsc(
                hash, RecipeSource.AI_GENERATED, RecipeStatus.PUBLISHED);
        if (!reused.isEmpty()) {
            UUID jobId = jobStore.create(new Job(userId, AiRecipeJobStatus.COMPLETED,
                    reused.stream().map(Recipe::getId).toList()));
            return completed(jobId, userId, reused);
        }

        UUID jobId = jobStore.create(new Job(userId, AiRecipeJobStatus.PENDING, List.of()));
        try {
            executor.execute(() -> generate(jobId, userId, owned, hash));
        } catch (RejectedExecutionException e) {
            jobStore.save(jobId, Job.failed(userId, ErrorCode.TOO_MANY_REQUESTS));
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return AiRecommendationResponse.pending(jobId);
    }

    public AiRecommendationResponse getJob(Long userId, UUID jobId) {
        Job job = jobStore.find(jobId).filter(j -> j.userId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
        if (job.status() == AiRecipeJobStatus.PENDING) {
            return AiRecommendationResponse.pending(jobId);
        }
        if (job.status() == AiRecipeJobStatus.FAILED) {
            // 이전 버전이 남긴 errorCode 없는 작업은 일반 실패로 본다.
            return AiRecommendationResponse.failed(jobId,
                    job.errorCode() == null ? ErrorCode.AI_GENERATION_FAILED : job.errorCode());
        }
        return completed(jobId, userId, recipeRepository.findAllById(job.recipeIds()));
    }

    /**
     * 전용 스레드 풀에서 실행한다. 실패해도 예외를 밖으로 던지지 않고 작업을 원인 코드와 함께 FAILED로 남긴다.
     * shortcut: 같은 재료 집합을 동시에 요청하면 둘 다 생성해 같은 해시 레시피가 늘어난다, 비용이 문제되면 해시 단위 Redis 락으로 합친다.
     */
    void generate(UUID jobId, Long userId, List<Ingredient> owned, String hash) {
        try {
            List<AiRecipeDraft> drafts = aiRecipeClient.generate(owned.stream().map(Ingredient::getName).toList());
            List<Recipe> recipes = draftValidator.toRecipes(drafts, owned.stream().map(Ingredient::getId).toList(), hash);
            List<Long> ids = recipeRepository.saveAll(recipes).stream().map(Recipe::getId).toList();
            jobStore.save(jobId, ids.isEmpty() ? Job.failed(userId, ErrorCode.AI_GENERATION_FAILED)
                    : new Job(userId, AiRecipeJobStatus.COMPLETED, ids));
        } catch (BusinessException e) {
            log.warn("AI 레시피 생성 실패: jobId={}, code={}", jobId, e.getErrorCode().getCode(), e);
            jobStore.save(jobId, Job.failed(userId, e.getErrorCode()));
        } catch (RuntimeException e) {
            log.warn("AI 레시피 생성 실패: jobId={}", jobId, e);
            jobStore.save(jobId, Job.failed(userId, ErrorCode.AI_GENERATION_FAILED));
        }
    }

    private AiRecommendationResponse completed(UUID jobId, Long userId, List<Recipe> recipes) {
        Set<Long> allergyIds = excludedIngredientRepository.findAllByUserIdOrderByIngredientIdAsc(userId).stream()
                .filter(e -> e.getType() == ExclusionType.ALLERGY)
                .map(e -> e.getIngredient().getId())
                .collect(Collectors.toSet());
        List<RecipeDetailResponse> visible = recipes.stream()
                .filter(r -> r.getStatus() == RecipeStatus.PUBLISHED)
                .filter(r -> r.getIngredients().stream().noneMatch(i -> allergyIds.contains(i.getIngredient().getId())))
                .sorted(Comparator.comparing(Recipe::getId))
                .map(RecipeDetailResponse::from)
                .toList();
        return AiRecommendationResponse.completed(jobId, visible);
    }
}
