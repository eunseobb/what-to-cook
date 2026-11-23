package com.whattocook.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 Redis(Testcontainers)에서 사용자별 한도와 창 만료를 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
class AiRateLimiterTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static AiRateLimiter rateLimiter;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        rateLimiter = new AiRateLimiter(redisTemplate, new AiRecipeProperties(1, 0, Duration.ofMinutes(1),
                new AiRecipeProperties.RateLimit(2, Duration.ofMillis(500))));
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("창 안에서 사용자별로 한도까지만 허용하고, 창이 지나면 다시 허용한다")
    void limitPerUserAndWindow() throws InterruptedException {
        assertThat(rateLimiter.tryAcquire(1L)).isTrue();
        assertThat(rateLimiter.tryAcquire(1L)).isTrue();
        assertThat(rateLimiter.tryAcquire(1L)).isFalse();
        assertThat(rateLimiter.tryAcquire(2L)).isTrue();

        Thread.sleep(700);
        assertThat(rateLimiter.tryAcquire(1L)).isTrue();
    }
}
