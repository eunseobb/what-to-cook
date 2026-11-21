package com.whattocook.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret                HS256 서명 키. 32바이트(256비트) 이상이어야 한다.
 * @param accessTokenValidity   Access 토큰 유효기간
 * @param refreshTokenValidity  Refresh 토큰 유효기간 (Redis TTL)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenValidity, Duration refreshTokenValidity) {
}
