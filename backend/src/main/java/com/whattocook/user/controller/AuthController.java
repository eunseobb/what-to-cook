package com.whattocook.user.controller;

import com.whattocook.user.dto.LoginRequest;
import com.whattocook.user.dto.RefreshTokenRequest;
import com.whattocook.user.dto.SignupRequest;
import com.whattocook.user.dto.SignupResponse;
import com.whattocook.user.dto.TokenResponse;
import com.whattocook.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입·로그인·토큰 재발급·로그아웃")
@SecurityRequirements   // 인증 없이 호출하는 API
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return new SignupResponse(authService.signup(request));
    }

    @Operation(summary = "로그인", description = "Access 토큰과 Refresh 토큰을 발급한다.")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "토큰 재발급", description = "Refresh 토큰을 새 토큰 쌍으로 교체한다. 쓴 Refresh 토큰은 무효가 된다.")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "로그아웃", description = "Refresh 토큰을 폐기한다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }
}
