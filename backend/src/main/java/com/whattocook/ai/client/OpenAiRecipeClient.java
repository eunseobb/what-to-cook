package com.whattocook.ai.client;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Component;

/**
 * {@link AiRecipeClient}의 OpenAI 구현. 응답은 JSON Schema(strict)로 고정한다. 모델은 spring.ai.openai.chat.options.model로 바꾼다.
 */
@Component
public class OpenAiRecipeClient implements AiRecipeClient {

    private static final String SYSTEM_PROMPT = """
            너는 한국 가정 요리 전문가다. 사용자가 가진 재료를 주재료로 쓰는 현실적인 레시피를 1~3개 만든다.
            - 사용자 재료를 최대한 쓰고, 그 밖의 재료는 흔한 기본 재료·양념만 조금 더한다.
            - 재료 이름은 수식어 없는 한국어 표준 명칭으로 쓴다 (예: "다진 대파" 대신 "대파", "계란" 대신 "달걀").
            - 분량은 0보다 큰 숫자, 단위는 g, ml, 개, 큰술, 작은술, 컵, 꼬집 중에서 쓴다. "약간"은 꼬집으로 표현한다.
            - 1인분 기준으로 현실적인 분량을 쓴다. 조리 단계는 한 단계에 한 동작씩 쓴다.
            """;

    private final ChatClient chatClient;
    private final BeanOutputConverter<AiRecipeDraft.Drafts> converter =
            new BeanOutputConverter<>(AiRecipeDraft.Drafts.class);

    public OpenAiRecipeClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public List<AiRecipeDraft> generate(List<String> ingredientNames) {
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
