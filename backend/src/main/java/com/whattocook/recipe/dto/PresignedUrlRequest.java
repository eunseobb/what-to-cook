package com.whattocook.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param fileName    원본 파일 이름. 확장자 검증에만 쓰고 저장 경로에는 쓰지 않는다.
 * @param contentType 이미지 MIME 타입 (image/jpeg, image/png, image/webp)
 * @param fileSize    파일 크기(바이트). 업로드 요청의 Content-Length가 이 값과 같아야 한다.
 */
public record PresignedUrlRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        @NotNull @Positive Long fileSize) {
}
