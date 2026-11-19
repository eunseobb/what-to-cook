package com.whattocook.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-only-jwt-secret-0123456789-abcdefghij";

    private final JwtTokenProvider provider = providerWith(SECRET, Duration.ofMinutes(30));

    @Test
    @DisplayName("발급한 Access 토큰에서 userId를 꺼낸다")
    void roundTrip() {
        String token = provider.createAccessToken(42L);

        assertThat(provider.parseUserId(token)).contains(42L);
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void expired() {
        String token = providerWith(SECRET, Duration.ofSeconds(-1)).createAccessToken(42L);

        assertThat(provider.parseUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거부한다")
    void wrongSignature() {
        String token = providerWith("another-secret-key-for-testing-0123456789", Duration.ofMinutes(30))
                .createAccessToken(42L);

        assertThat(provider.parseUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("형식이 깨진 토큰은 거부한다")
    void malformed() {
        assertThat(provider.parseUserId("not-a-jwt")).isEmpty();
        assertThat(provider.parseUserId("")).isEmpty();
    }

    private static JwtTokenProvider providerWith(String secret, Duration accessValidity) {
        return new JwtTokenProvider(new JwtProperties(secret, accessValidity, Duration.ofDays(14)));
    }
}
