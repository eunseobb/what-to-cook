package com.whattocook.community.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.whattocook.community.repository.RecipeLikeRepository;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.repository.UserRepository;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisRecipeLikeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeLikeRepository recipeLikeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private RedisRecipeLikeService service;

    @BeforeEach
    void setUp() {
        given(redisTemplate.<Object, Object>opsForHash()).willReturn(hashOperations);
        given(recipeRepository.existsByIdAndStatus(5L, RecipeStatus.PUBLISHED)).willReturn(true);
    }

    @Test
    @DisplayName("좋아요를 저장한 뒤 Redis 증감분을 1 올린다")
    void like() {
        service.like(1L, 5L);

        verify(recipeLikeRepository).saveAndFlush(any());
        verify(hashOperations).increment(RedisRecipeLikeService.DELTA_KEY, "5", 1);
    }

    @Test
    @DisplayName("유니크 제약에 걸리면 L001로 바꾸고 Redis는 건드리지 않는다")
    void likeDuplicate() {
        given(recipeLikeRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> service.like(1L, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_LIKED);
        verifyNoInteractions(hashOperations);
    }

    @Test
    @DisplayName("게시 중이 아닌(삭제된) 레시피는 R001")
    void likeDeletedRecipe() {
        assertThatThrownBy(() -> service.like(1L, 6L))
                .extracting("errorCode").isEqualTo(ErrorCode.RECIPE_NOT_FOUND);
        verify(recipeLikeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("좋아요 취소는 지운 행이 있을 때만 증감분을 1 내리고, 없으면 L002")
    void unlike() {
        given(recipeLikeRepository.deleteByUserIdAndRecipeId(1L, 5L)).willReturn(1, 0);

        service.unlike(1L, 5L);
        verify(hashOperations).increment(RedisRecipeLikeService.DELTA_KEY, "5", -1);

        assertThatThrownBy(() -> service.unlike(1L, 5L))
                .extracting("errorCode").isEqualTo(ErrorCode.LIKE_NOT_FOUND);
    }

    @Test
    @DisplayName("반영은 증감분 스냅샷을 떼어 DB에 더하고(0은 건너뜀) 스냅샷을 지운다")
    @SuppressWarnings("unchecked")
    void flush() {
        given(redisTemplate.hasKey(RedisRecipeLikeService.FLUSHING_KEY)).willReturn(false);
        given(redisTemplate.hasKey(RedisRecipeLikeService.DELTA_KEY)).willReturn(true);
        given(hashOperations.entries(RedisRecipeLikeService.FLUSHING_KEY)).willReturn(Map.of("5", "3", "6", "0"));
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service.flushLikeCounts();

        verify(redisTemplate).rename(RedisRecipeLikeService.DELTA_KEY, RedisRecipeLikeService.FLUSHING_KEY);
        verify(recipeRepository).addLikeCount(5L, 3);
        verify(recipeRepository, never()).addLikeCount(6L, 0);
        verify(redisTemplate).delete(RedisRecipeLikeService.FLUSHING_KEY);
    }

    @Test
    @DisplayName("모인 증감분이 없으면 아무것도 하지 않는다")
    void flushNothing() {
        given(redisTemplate.hasKey(any())).willReturn(false);

        service.flushLikeCounts();

        verify(recipeRepository, never()).addLikeCount(anyLong(), anyInt());
        verify(redisTemplate, never()).rename(any(), any());
    }
}
