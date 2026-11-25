package com.whattocook.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.Difficulty;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

/** 실제 OpenAI 대신 ChatModel을 Mock으로 두고 요청 형식·응답 변환과 장애 대응(타임아웃·재시도·서킷 브레이커)을 확인한다. */
class OpenAiRecipeClientTest {

    private static final String JSON = """
            {"recipes":[{"title":"달걀볶음밥","difficulty":"EASY","cookTimeMinutes":15,"servings":1,
              "ingredients":[{"name":"달걀","quantity":2,"unit":"개"}],"steps":["달걀을 푼다","밥과 볶는다"]}]}
            """;

    private final ChatModel chatModel = mock(ChatModel.class);
    private final OpenAiRecipeClient client = client(chatModel);

    /** application.yml의 openai 인스턴스와 같은 규칙에 대기 시간·창 크기만 줄였다. */
    private static OpenAiRecipeClient client(ChatModel chatModel) {
        return new OpenAiRecipeClient(ChatClient.builder(chatModel),
                RetryRegistry.of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1))
                        .retryExceptions(TransientAiException.class).build()),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(200)).build()),
                CircuitBreakerRegistry.of(CircuitBreakerConfig.custom().slidingWindowSize(4).minimumNumberOfCalls(4)
                        .failureRateThreshold(50).waitDurationInOpenState(Duration.ofMinutes(1)).build()));
    }

    private static ChatResponse response(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    @Test
    @DisplayName("strict JSON Schema로 요청하고 응답 JSON을 레시피 초안으로 바꾼다")
    @SuppressWarnings("unchecked")
    void generate() {
        given(chatModel.call(any(Prompt.class))).willReturn(response(JSON));

        List<AiRecipeDraft> drafts = client.generate(List.of("달걀", "밥"));

        assertThat(drafts).singleElement().satisfies(d -> {
            assertThat(d.title()).isEqualTo("달걀볶음밥");
            assertThat(d.difficulty()).isEqualTo(Difficulty.EASY);
            assertThat(d.ingredients()).singleElement().satisfies(i -> assertThat(i.quantity()).isEqualByComparingTo("2"));
            assertThat(d.steps()).hasSize(2);
        });

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getText()).contains("달걀, 밥");
        ResponseFormat format = ((OpenAiChatOptions) prompt.getValue().getOptions()).getResponseFormat();
        assertThat(format.getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
        assertThat(format.getJsonSchema().getStrict()).isTrue();
        // strict 모드는 모든 필드가 required여야 한다.
        Map<String, Object> schema = format.getJsonSchema().getSchema();
        Map<String, Object> recipe = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                schema.get("properties")).get("recipes")).get("items");
        assertThat((List<String>) recipe.get("required"))
                .containsExactlyInAnyOrder("title", "difficulty", "cookTimeMinutes", "servings", "ingredients", "steps");
    }

    @Test
    @DisplayName("일시 오류(5xx·429)는 재시도해 성공하고, 비일시 오류(4xx)는 재시도하지 않는다")
    void retryOnlyTransient() {
        given(chatModel.call(any(Prompt.class)))
                .willThrow(new TransientAiException("503"))
                .willReturn(response(JSON));
        assertThat(client.generate(List.of("달걀"))).hasSize(1);
        verify(chatModel, times(2)).call(any(Prompt.class));

        ChatModel badRequest = mock(ChatModel.class);
        given(badRequest.call(any(Prompt.class))).willThrow(new NonTransientAiException("400"));
        OpenAiRecipeClient other = client(badRequest);
        assertThatThrownBy(() -> other.generate(List.of("달걀"))).isInstanceOf(NonTransientAiException.class);
        verify(badRequest, times(1)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("응답이 타임아웃을 넘기면 AI004로 실패한다")
    void timeout() {
        given(chatModel.call(any(Prompt.class))).willAnswer(inv -> {
            Thread.sleep(2_000);
            return response(JSON);
        });

        assertThatThrownBy(() -> client.generate(List.of("달걀")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AI_TIMEOUT);
    }

    @Test
    @DisplayName("실패가 쌓여 서킷이 열리면 OpenAI를 부르지 않고 바로 AI003으로 실패한다")
    void circuitOpens() {
        given(chatModel.call(any(Prompt.class))).willThrow(new NonTransientAiException("400"));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.generate(List.of("달걀"))).isInstanceOf(NonTransientAiException.class);
        }

        assertThatThrownBy(() -> client.generate(List.of("달걀")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE);
        verify(chatModel, times(4)).call(any(Prompt.class));
    }
}
