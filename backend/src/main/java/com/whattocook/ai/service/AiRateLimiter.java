package com.whattocook.ai.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 사용자별 LLM 호출 한도. 여러 인스턴스가 같은 한도를 보도록 Redis에 센다.
 * 첫 요청부터 window 동안 maxRequests까지 허용한다(고정 창). INCR과 만료 설정을 Lua로 묶어, 만료 없는 키가 남아 영구 차단되는 일을 막는다.
 * shortcut: 창 경계에서 최대 2배까지 몰릴 수 있다, 엄밀한 제한이 필요하면 sorted set 기반 슬라이딩 윈도우로 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

    private static final String KEY_PREFIX = "ai:rate-limit:";
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AiRecipeProperties properties;

    /** @return 한도 안이면 true. 호출할 때마다 한 번으로 센다. */
    public boolean tryAcquire(Long userId) {
        AiRecipeProperties.RateLimit limit = properties.rateLimit();
        Long count = redisTemplate.execute(INCREMENT, List.of(KEY_PREFIX + userId),
                String.valueOf(limit.window().toMillis()));
        return count != null && count <= limit.maxRequests();
    }
}
