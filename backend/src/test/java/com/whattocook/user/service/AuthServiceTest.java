package com.whattocook.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("unused", Duration.ofMinutes(30), REFRESH_TTL);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder,
                jwtTokenProvider, properties);
    }

    @Test
    @DisplayName("회원가입 시 이메일을 정규화하고 비밀번호를 해시해서 저장한다")
    void signup() {
        given(userRepository.existsByEmail("chef@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("hashed");
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> withId(inv.getArgument(0), 1L));

        Long userId = authService.signup(new SignupRequest(" Chef@Example.com ", "password123", " 요리사 "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(userId).isEqualTo(1L);
        assertThat(captor.getValue().getEmail()).isEqualTo("chef@example.com");
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(captor.getValue().getNickname()).isEqualTo("요리사");
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 DUPLICATE_EMAIL 예외가 난다")
    void signupDuplicateEmail() {
        given(userRepository.existsByEmail("chef@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("chef@example.com", "password123", "요리사")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 가입으로 유니크 제약에 걸려도 DUPLICATE_EMAIL 예외로 바꾼다")
    void signupRaceCondition() {
        given(userRepository.existsByEmail("chef@example.com")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("hashed");
        given(userRepository.saveAndFlush(any(User.class))).willThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> authService.signup(new SignupRequest("chef@example.com", "password123", "요리사")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("로그인에 성공하면 Access 토큰을 발급하고 Refresh 토큰을 Redis에 저장한다")
    void login() {
        User user = withId(new User("chef@example.com", "hashed", "요리사"), 1L);
        given(userRepository.findByEmail("chef@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "hashed")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(1L)).willReturn("access");

        TokenResponse tokens = authService.login(new LoginRequest("CHEF@example.com", "password123"));

        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(tokens.refreshToken()).hasSizeGreaterThanOrEqualTo(43);   // 32바이트 Base64URL
        verify(refreshTokenRepository).save(tokens.refreshToken(), 1L, REFRESH_TTL);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 INVALID_CREDENTIALS 예외가 난다")
    void loginWrongPassword() {
        User user = withId(new User("chef@example.com", "hashed", "요리사"), 1L);
        given(userRepository.findByEmail("chef@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("chef@example.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("없는 이메일이면 비밀번호 오류와 같은 INVALID_CREDENTIALS 예외가 난다")
    void loginUnknownEmail() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("재발급하면 기존 Refresh 토큰을 소비하고 새 토큰 쌍을 발급한다")
    void refresh() {
        given(refreshTokenRepository.consume("old-refresh")).willReturn(Optional.of(1L));
        given(jwtTokenProvider.createAccessToken(1L)).willReturn("new-access");

        TokenResponse tokens = authService.refresh("old-refresh");

        assertThat(tokens.accessToken()).isEqualTo("new-access");
        assertThat(tokens.refreshToken()).isNotEqualTo("old-refresh");
        verify(refreshTokenRepository).save(eq(tokens.refreshToken()), eq(1L), eq(REFRESH_TTL));
    }

    @Test
    @DisplayName("저장되지 않았거나 이미 쓴 Refresh 토큰이면 INVALID_REFRESH_TOKEN 예외가 난다")
    void refreshInvalidToken() {
        given(refreshTokenRepository.consume("used")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("used"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository, never()).save(anyString(), any(), any());
    }

    @Test
    @DisplayName("로그아웃하면 Refresh 토큰을 삭제한다")
    void logout() {
        authService.logout("refresh");

        verify(refreshTokenRepository).delete("refresh");
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
