package com.whattocook.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.whattocook.community.service.PessimisticLockRecipeLikeService;
import com.whattocook.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * app.like.strategy=lock(비관적 락) 방식이 같은 동시성 시나리오에서 정확한 카운트를 남기는지 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.like.strategy=lock")
@Import(TestcontainersConfiguration.class)
class PessimisticLockRecipeLikeIntegrationTest extends RecipeLikeConcurrencyTestSupport {

    @Override
    protected void syncLikeCount() {
        // 비관적 락 방식은 요청 트랜잭션에서 like_count를 바로 갱신한다.
    }

    @Test
    @DisplayName("설정으로 비관적 락 구현이 선택된다")
    void strategySelected() {
        assertThat(recipeLikeService).isInstanceOf(PessimisticLockRecipeLikeService.class);
    }
}
