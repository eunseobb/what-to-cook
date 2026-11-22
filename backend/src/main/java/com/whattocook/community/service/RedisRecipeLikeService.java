package com.whattocook.community.service;

import com.whattocook.community.domain.RecipeLike;
import com.whattocook.community.repository.RecipeLikeRepository;
import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.recipe.repository.RecipeRepository;
import com.whattocook.user.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Redis 원자 연산 방식(기본). 레시피 행을 잠그지 않고, 카운트 증감은 Redis 해시에 HINCRBY로 모은 뒤
 * {@link #flushLikeCounts()}가 주기적으로 recipe.like_count에 더한다. 그 사이 DB 카운트는 최대 반영 주기만큼 늦다.
 * 사용자당 1회 제약은 recipe_like 유니크 제약이 막는다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.like.strategy", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
public class RedisRecipeLikeService implements RecipeLikeService {

    /** 해시: 필드 = 레시피 id, 값 = 아직 DB에 반영하지 않은 증감분. */
    static final String DELTA_KEY = "recipe:like:delta";
    /** 반영 중인 스냅샷. DB 커밋 뒤에만 지워서, 반영이 실패하면 다음 주기에 다시 시도한다. */
    static final String FLUSHING_KEY = DELTA_KEY + ":flushing";

    private final RecipeRepository recipeRepository;
    private final RecipeLikeRepository recipeLikeRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;

    // Redis 증감은 트랜잭션 마지막에 한다: Redis가 실패하면 좋아요 행도 롤백돼 카운트와 어긋나지 않는다.
    @Override
    @Transactional
    public void like(Long userId, Long recipeId) {
        requirePublished(recipeId);
        try {
            recipeLikeRepository.saveAndFlush(new RecipeLike(
                    userRepository.getReferenceById(userId), recipeRepository.getReferenceById(recipeId)));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        redisTemplate.opsForHash().increment(DELTA_KEY, recipeId.toString(), 1);
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long recipeId) {
        requirePublished(recipeId);
        if (recipeLikeRepository.deleteByUserIdAndRecipeId(userId, recipeId) == 0) {
            throw new BusinessException(ErrorCode.LIKE_NOT_FOUND);
        }
        redisTemplate.opsForHash().increment(DELTA_KEY, recipeId.toString(), -1);
    }

    /**
     * 모인 증감분을 DB에 반영한다. RENAME으로 스냅샷을 떼어 내므로 반영 중에 들어온 증감은 새 키에 쌓인다.
     * shortcut: synchronized는 한 인스턴스 안에서만 중복 실행을 막는다, 여러 인스턴스로 늘리면 ShedLock 같은 분산 락을 건다.
     */
    @Scheduled(fixedDelayString = "${app.like.flush-interval:PT10S}")
    public synchronized void flushLikeCounts() {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(FLUSHING_KEY))) {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(DELTA_KEY))) {
                return;
            }
            redisTemplate.rename(DELTA_KEY, FLUSHING_KEY);
        }
        Map<Object, Object> deltas = redisTemplate.opsForHash().entries(FLUSHING_KEY);
        transactionTemplate.executeWithoutResult(status -> deltas.forEach((recipeId, delta) -> {
            int value = Integer.parseInt((String) delta);
            if (value != 0) {
                recipeRepository.addLikeCount(Long.valueOf((String) recipeId), value);
            }
        }));
        // shortcut: DB 커밋 뒤 이 DEL이 실패하면 다음 주기에 같은 증감이 한 번 더 더해진다, 정확도가 더 중요해지면 반영 이력 키로 멱등하게 바꾼다.
        redisTemplate.delete(FLUSHING_KEY);
        log.debug("좋아요 카운트 반영: {}건", deltas.size());
    }

    private void requirePublished(Long recipeId) {
        if (!recipeRepository.existsByIdAndStatus(recipeId, RecipeStatus.PUBLISHED)) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
    }
}
