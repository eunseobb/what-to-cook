package com.whattocook.ai.client;

import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI로 레시피 초안을 만든다. 응답은 JSON Schema(strict)로 고정한다.
 * LLM 호출은 {@link #generate(List)} 한 곳뿐이다: Resilience4j 인스턴스 "openai"(application.yml)로 타임아웃·재시도·서킷 브레이커를 건다.
 */
@Component
public class AiRecipeClient {

    private static final String SYSTEM_PROMPT = """
            너는 한국 가정 요리 전문가다. 사용자가 가진 재료를 주재료로 쓰는 현실적인 레시피를 1~3개 만든다.
            - 사용자 재료를 최대한 쓰고, 그 밖의 재료는 흔한 기본 재료·양념만 조금 더한다.
            - 재료 이름은 수식어 없는 한국어 표준 명칭으로 쓴다 (예: "다진 대파" 대신 "대파", "계란" 대신 "달걀").
            - 분량은 0보다 큰 숫자, 단위는 g, ml, 개, 큰술, 작은술, 컵, 꼬집 중에서 쓴다. "약간"은 꼬집으로 표현한다.
            - 1인분 기준으로 현실적인 분량을 쓴다. 조리 단계는 한 단계에 한 동작씩 쓴다.
            """;

    static final String RESILIENCE_INSTANCE = "openai";

    private final ChatClient chatClient;
    private final BeanOutputConverter<AiRecipeDraft.Drafts> converter =
            new BeanOutputConverter<>(AiRecipeDraft.Drafts.class);
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    /**
     * 타임아웃을 걸려면 호출을 다른 스레드에서 돌려야 한다. 호출하는 쪽(aiRecipeExecutor) 수만큼만 동시에 쓰인다.
     * shortcut: 인터럽트를 무시하고 멈춘 호출은 스레드를 계속 잡는다, 누적되면 spring.http.client.read-timeout을 함께 건다.
     */
    private final ExecutorService callExecutor = Executors.newCachedThreadPool(new CustomizableThreadFactory("openai-call-"));

    public AiRecipeClient(ChatClient.Builder chatClientBuilder, RetryRegistry retryRegistry,
                          TimeLimiterRegistry timeLimiterRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.retry = retryRegistry.retry(RESILIENCE_INSTANCE);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(RESILIENCE_INSTANCE);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE);
    }

    /**
     * 재시도(일시 오류만, 지수 백오프) → 서킷 브레이커 → 시도마다 타임아웃 순으로 감싸 호출한다.
     * 시도마다 서킷 브레이커가 실패를 세고, 열리면 재시도 없이 바로 실패한다.
     *
     * @param ingredientNames 사용자가 가진 표준 재료 이름
     * @throws BusinessException 서킷이 열려 있으면 AI003, 타임아웃이면 AI004
     */
    public List<AiRecipeDraft> generate(List<String> ingredientNames) {
        Callable<List<AiRecipeDraft>> timed = TimeLimiter.decorateFutureSupplier(timeLimiter,
                () -> callExecutor.submit(() -> call(ingredientNames)));
        try {
            return Retry.decorateCallable(retry, CircuitBreaker.decorateCallable(circuitBreaker, timed)).call();
        } catch (CallNotPermittedException e) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCode.AI_TIMEOUT);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 호출 실패", e);
        }
    }

    @PreDestroy
    void shutdown() {
        callExecutor.shutdownNow();
    }

    private List<AiRecipeDraft> call(List<String> ingredientNames) {
        String content = chatClient.prompt()
                .options(OpenAiChatOptions.builder()
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, converter.getJsonSchema()))
                        .build())
                .system(SYSTEM_PROMPT)
                .user("가진 재료: " + String.join(", ", ingredientNames))
                .call()
                .content();
        AiRecipeDraft.Drafts drafts = content == null ? null : converter.convert(content);
        return drafts == null || drafts.recipes() == null ? List.of() : drafts.recipes();
    }
}
