package com.whattocook.user.dto;

/**
 * @param accessToken  보호된 API 호출 시 Authorization: Bearer 헤더로 보낸다.
 * @param refreshToken 재발급과 로그아웃에만 쓴다. 재발급하면 새 값으로 바뀌고 이전 값은 무효가 된다.
 */
public record TokenResponse(String accessToken, String refreshToken) {
}
