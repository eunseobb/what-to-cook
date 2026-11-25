package com.whattocook.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.whattocook.recipe.domain.Difficulty;
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

/** 실제 OpenAI 대신 ChatModel을 Mock으로 두고 요청 형식과 응답 변환만 확인한다. */
class OpenAiRecipeClientTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final OpenAiRecipeClient client = new OpenAiRecipeClient(ChatClient.builder(chatModel));

    @Test
    @DisplayName("strict JSON Schema로 요청하고 응답 JSON을 레시피 초안으로 바꾼다")
    @SuppressWarnings("unchecked")
    void generate() {
        given(chatModel.call(any(Prompt.class))).willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"recipes":[{"title":"달걀볶음밥","difficulty":"EASY","cookTimeMinutes":15,"servings":1,
                  "ingredients":[{"name":"달걀","quantity":2,"unit":"개"}],"steps":["달걀을 푼다","밥과 볶는다"]}]}
                """)))));

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
}
