package com.whattocook.recipe.service;

import com.whattocook.global.config.StorageProperties;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.dto.PresignedUrlRequest;
import com.whattocook.recipe.dto.PresignedUrlResponse;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 레시피 이미지(대표·단계) 업로드용 Presigned PUT URL을 발급한다. 파일은 클라이언트가 스토리지에 직접 올린다.
 */
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Map<String, Set<String>> EXTENSIONS_BY_TYPE = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp"));

    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    public PresignedUrlResponse issueRecipeImageUrl(Long userId, PresignedUrlRequest request) {
        String contentType = request.contentType().toLowerCase(Locale.ROOT);
        Set<String> extensions = EXTENSIONS_BY_TYPE.get(contentType);
        if (extensions == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        String fileName = request.fileName();
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extensions.contains(extension)) {
            throw new BusinessException(ErrorCode.IMAGE_EXTENSION_MISMATCH);
        }
        if (request.fileSize() > storageProperties.maxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }

        // 객체 키는 서버가 만든다. 사용자 입력(파일 이름)은 경로에 넣지 않는다.
        String key = "recipes/%d/%s.%s".formatted(userId, UUID.randomUUID(), extension);
        // Content-Type과 Content-Length를 서명에 넣어, 다른 형식이나 신고와 다른 크기의 파일은 스토리지가 거부한다.
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(request.fileSize())
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(storageProperties.presignExpiry())
                .putObjectRequest(putObject)
                .build());

        return new PresignedUrlResponse(presigned.url().toString(),
                storageProperties.publicBaseUrl() + "/" + key, presigned.expiration());
    }
}
