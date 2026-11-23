package com.whattocook.user.controller;

import com.whattocook.user.dto.OnboardingRequest;
import com.whattocook.user.dto.OnboardingResponse;
import com.whattocook.user.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Onboarding", description = "선호 태그·알레르기·비선호 재료")
@RestController
@RequestMapping("/api/users/me/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @Operation(summary = "내 온보딩 정보 조회")
    @GetMapping
    public OnboardingResponse get(@AuthenticationPrincipal Long userId) {
        return onboardingService.get(userId);
    }

    @Operation(summary = "내 온보딩 정보 저장",
            description = "선호 태그·알레르기·비선호 재료를 통째로 교체한다. 생략하거나 비운 목록은 모두 지운다.")
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replace(@AuthenticationPrincipal Long userId, @Valid @RequestBody OnboardingRequest request) {
        onboardingService.replace(userId, request);
    }
}
