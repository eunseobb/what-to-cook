package com.whattocook.recipe.controller;

import com.whattocook.recipe.dto.PresignedUrlRequest;
import com.whattocook.recipe.dto.PresignedUrlResponse;
import com.whattocook.recipe.service.ImageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Upload", description = "레시피 이미지 업로드")
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final ImageUploadService imageUploadService;

    @Operation(summary = "이미지 업로드 URL 발급",
            description = "대표·단계 이미지용 Presigned PUT URL을 발급한다. uploadUrl로 파일을 PUT할 때 "
                    + "Content-Type과 Content-Length를 요청한 값 그대로 보내고, 업로드가 끝나면 imageUrl을 레시피 등록·수정에 쓴다.")
    @PostMapping("/presigned-url")
    public PresignedUrlResponse issuePresignedUrl(@AuthenticationPrincipal Long userId,
                                                  @Valid @RequestBody PresignedUrlRequest request) {
        return imageUploadService.issueRecipeImageUrl(userId, request);
    }
}
