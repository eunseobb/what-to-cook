package com.whattocook.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whattocook.ai.dto.AiRecipeJobStatus;
import com.whattocook.global.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 폴링용 AI 생성 작업 상태. 여러 인스턴스 중 어디로 폴링이 와도 보이게 Redis에 TTL과 함께 둔다.
 */
@Component
@RequiredArgsConstructor
public class AiRecipeJobStore {

    private static final String KEY_PREFIX = "ai:recipe-job:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiRecipeProperties properties;

    public UUID create(Job job) {
        UUID jobId = UUID.randomUUID();
        save(jobId, job);
        return jobId;
    }

    public void save(UUID jobId, Job job) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jobId, objectMapper.writeValueAsString(job), properties.jobTtl());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 작업 상태를 직렬화할 수 없습니다.", e);
        }
    }

    public Optional<Job> find(UUID jobId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + jobId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, Job.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 작업 상태를 읽을 수 없습니다: " + jobId, e);
        }
    }

    /**
     * @param userId    요청한 사용자. 다른 사용자는 조회할 수 없다.
     * @param recipeIds 완료 시 결과 레시피 id
     * @param errorCode 실패 시 원인. 그 밖에는 null
     */
    public record Job(Long userId, AiRecipeJobStatus status, List<Long> recipeIds, ErrorCode errorCode) {

        public Job(Long userId, AiRecipeJobStatus status, List<Long> recipeIds) {
            this(userId, status, recipeIds, null);
        }

        public static Job failed(Long userId, ErrorCode errorCode) {
            return new Job(userId, AiRecipeJobStatus.FAILED, List.of(), errorCode);
        }
    }
}
