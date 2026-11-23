package com.whattocook.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whattocook.global.config.S3Config;
import com.whattocook.global.config.StorageProperties;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.dto.PresignedUrlRequest;
import com.whattocook.recipe.dto.PresignedUrlResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 실제 S3Presigner로 서명한다. 서명은 네트워크 없이 로컬에서 계산된다.
 */
class ImageUploadServiceTest {

    private final StorageProperties properties = new StorageProperties("http://localhost:9000", "us-east-1",
            "test-bucket", "test-access-key", "test-secret-key", "https://cdn.example.com",
            Duration.ofMinutes(5), DataSize.ofMegabytes(5));
    private final S3Presigner presigner = new S3Config().s3Presigner(properties);
    private final ImageUploadService service = new ImageUploadService(presigner, properties);

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    @DisplayName("서버가 만든 키로 PUT URL을 발급하고, 형식·크기를 서명에 넣으며, 최종 이미지 URL을 함께 준다")
    void issue() {
        PresignedUrlResponse response = service.issueRecipeImageUrl(7L,
                new PresignedUrlRequest("../../etc/passwd 김치찌개.JPG", "image/jpeg", 1024L));

        assertThat(response.imageUrl()).matches("https://cdn\\.example\\.com/recipes/7/[0-9a-f-]{36}\\.jpg");
        String key = response.imageUrl().substring("https://cdn.example.com/".length());
        assertThat(response.uploadUrl())
                .startsWith("http://localhost:9000/test-bucket/" + key + "?")
                .contains("X-Amz-Expires=300")
                .contains("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost");
        assertThat(response.expiresAt()).isBetween(Instant.now().plusSeconds(290), Instant.now().plusSeconds(301));
    }

    @Test
    @DisplayName("이미지가 아닌 형식은 거부한다")
    void rejectNonImage() {
        assertThatThrownBy(() -> service.issueRecipeImageUrl(7L,
                new PresignedUrlRequest("a.svg", "image/svg+xml", 1024L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    @DisplayName("확장자가 형식과 맞지 않거나 없으면 거부한다")
    void rejectExtensionMismatch() {
        assertThatThrownBy(() -> service.issueRecipeImageUrl(7L,
                new PresignedUrlRequest("a.html", "image/png", 1024L)))
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_EXTENSION_MISMATCH);
        assertThatThrownBy(() -> service.issueRecipeImageUrl(7L,
                new PresignedUrlRequest("png", "image/png", 1024L)))
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_EXTENSION_MISMATCH);
    }

    @Test
    @DisplayName("최대 크기를 넘으면 거부한다")
    void rejectTooLarge() {
        long max = DataSize.ofMegabytes(5).toBytes();
        assertThat(service.issueRecipeImageUrl(7L, new PresignedUrlRequest("a.png", "image/png", max)))
                .isNotNull();
        assertThatThrownBy(() -> service.issueRecipeImageUrl(7L,
                new PresignedUrlRequest("a.png", "image/png", max + 1)))
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
    }
}
