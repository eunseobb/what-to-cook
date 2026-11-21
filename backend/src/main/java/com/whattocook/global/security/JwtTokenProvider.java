package com.whattocook.global.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Access 토큰(JWT) 발급과 검증. subject에 userId를 담는다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValidityMillis;

    public JwtTokenProvider(JwtProperties properties) {
        // 키가 256비트보다 짧으면 여기서 예외가 나서 기동에 실패한다.
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMillis = properties.accessTokenValidity().toMillis();
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenValidityMillis))
                .signWith(key)
                .compact();
    }

    /** 서명·만료가 유효하면 userId를, 아니면 빈 값을 돌려준다. */
    public Optional<Long> parseUserId(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
