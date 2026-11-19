package com.whattocook.user.service;

import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.global.security.JwtProperties;
import com.whattocook.global.security.JwtTokenProvider;
import com.whattocook.user.domain.User;
import com.whattocook.user.dto.LoginRequest;
import com.whattocook.user.dto.SignupRequest;
import com.whattocook.user.dto.TokenResponse;
import com.whattocook.user.repository.RefreshTokenRepository;
import com.whattocook.user.repository.UserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access 토큰은 짧게 쓰는 JWT, Refresh 토큰은 Redis에 저장하는 불투명(랜덤) 값이다.
 * Refresh 토큰은 한 번 쓰면 폐기하고 새로 발급한다(rotation).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public Long signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        try {
            User user = userRepository.saveAndFlush(
                    new User(email, passwordEncoder.encode(request.password()), request.nickname().trim()));
            return user.getId();
        } catch (DataIntegrityViolationException e) {
            // 같은 이메일로 동시에 가입한 경우 유니크 제약에 걸린다.
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        return issueTokens(user.getId());
    }

    public TokenResponse refresh(String refreshToken) {
        Long userId = refreshTokenRepository.consume(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issueTokens(userId);
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.delete(refreshToken);
    }

    private TokenResponse issueTokens(Long userId) {
        String refreshToken = generateRefreshToken();
        refreshTokenRepository.save(refreshToken, userId, jwtProperties.refreshTokenValidity());
        return new TokenResponse(jwtTokenProvider.createAccessToken(userId), refreshToken);
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
