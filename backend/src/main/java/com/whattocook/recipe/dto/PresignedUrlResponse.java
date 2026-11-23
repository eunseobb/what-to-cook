package com.whattocook.recipe.dto;

import java.time.Instant;

/**
 * @param uploadUrl 이 주소로 파일을 PUT한다. Content-Type과 Content-Length는 요청한 값 그대로 보내야 한다.
 * @param imageUrl  업로드 후 레시피 등록·수정 API의 mainImageUrl·imageUrl에 넣을 주소
 * @param expiresAt uploadUrl 만료 시각
 */
public record PresignedUrlResponse(String uploadUrl, String imageUrl, Instant expiresAt) {
}
