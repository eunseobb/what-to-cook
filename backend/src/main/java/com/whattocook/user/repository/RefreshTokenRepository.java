package com.whattocook.user.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Refresh 토큰 저장소 (Redis). key = auth:refresh:{token}, value = userId, TTL = 토큰 유효기간.
 * 토큰마다 키를 두므로 기기별로 여러 세션을 유지할 수 있다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(String refreshToken, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, String.valueOf(userId), ttl);
    }

    /** 토큰을 원자적으로 꺼내고 지운다(GETDEL). 같은 토큰으로 동시에 재발급해도 하나만 성공한다. */
    public Optional<Long> consume(String refreshToken) {
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + refreshToken))
                .map(Long::valueOf);
    }

    public void delete(String refreshToken) {
        redisTemplate.delete(KEY_PREFIX + refreshToken);
    }
}
