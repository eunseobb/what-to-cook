package com.whattocook.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * S3 호환 스토리지 설정.
 *
 * @param endpoint      S3 호환 스토리지 주소 (MinIO 등). 비우면 AWS S3 기본 주소를 쓴다.
 * @param region        리전
 * @param bucket        업로드 버킷
 * @param accessKey     액세스 키. 비우면 AWS 기본 자격 증명 체인(IAM 역할 등)을 쓴다.
 * @param secretKey     시크릿 키
 * @param publicBaseUrl 업로드된 객체를 읽을 공개 주소 (버킷 주소나 CDN). 최종 이미지 URL = publicBaseUrl/객체 키
 * @param presignExpiry Presigned URL 만료 시간
 * @param maxFileSize   업로드 허용 최대 파일 크기
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String endpoint, String region, String bucket, String accessKey, String secretKey,
                                String publicBaseUrl, Duration presignExpiry, DataSize maxFileSize) {
}
