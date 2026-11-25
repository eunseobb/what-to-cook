package com.whattocook.ai.client;

import java.util.List;

/**
 * LLM으로 레시피 초안을 만든다. 서비스는 이 인터페이스에만 의존하므로 LLM 제공자를 바꿀 때는 구현체만 바꾼다.
 * LLM 호출은 {@link #generate(List)} 한 곳뿐이다: 타임아웃·재시도·서킷 브레이커·Rate Limit은 이 메서드를 감싼다.
 */
public interface AiRecipeClient {

    /**
     * @param ingredientNames 사용자가 가진 표준 재료 이름
     * @return 검증 전 레시피 초안 1~3개. 응답이 비어 있으면 빈 목록
     */
    List<AiRecipeDraft> generate(List<String> ingredientNames);
}
