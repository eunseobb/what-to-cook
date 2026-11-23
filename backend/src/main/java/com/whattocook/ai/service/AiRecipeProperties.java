package com.whattocook.ai.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 레시피 생성 작업 설정.
 *
 * @param poolSize      LLM 호출 전용 스레드 수. 요청 스레드·DB 커넥션과 격리해 LLM 지연이 다른 API로 번지지 않게 한다.
 * @param queueCapacity 스레드가 모두 바쁠 때 대기시킬 작업 수. 넘치면 429로 거절한다.
 * @param jobTtl        폴링용 작업 상태를 Redis에 보관하는 시간
 * @param rateLimit     사용자별 LLM 호출 한도 (캐시 히트는 세지 않는다)
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiRecipeProperties(int poolSize, int queueCapacity, Duration jobTtl, RateLimit rateLimit) {

    public AiRecipeProperties {
        if (poolSize < 1 || queueCapacity < 0) {
            throw new IllegalArgumentException("app.ai.pool-size는 1 이상, queue-capacity는 0 이상이어야 합니다.");
        }
        if (jobTtl == null || jobTtl.isNegative() || jobTtl.isZero()) {
            throw new IllegalArgumentException("app.ai.job-ttl은 0보다 커야 합니다: " + jobTtl);
        }
        if (rateLimit == null) {
            throw new IllegalArgumentException("app.ai.rate-limit이 필요합니다.");
        }
    }

    /**
     * @param maxRequests window 동안 사용자 한 명이 LLM 생성을 요청할 수 있는 횟수
     * @param window      한도를 세는 시간 창
     */
    public record RateLimit(int maxRequests, Duration window) {

        public RateLimit {
            if (maxRequests < 1 || window == null || window.toMillis() < 1) {
                throw new IllegalArgumentException("app.ai.rate-limit.max-requests는 1 이상, window는 1ms 이상이어야 합니다.");
            }
        }
    }
}
