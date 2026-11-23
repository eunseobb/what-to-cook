package com.whattocook.community.service;

/**
 * 레시피 좋아요. 부하 테스트(CLAUDE.md 5절 시나리오 ③)에서 동시성 처리 방식을 비교하려고
 * 구현을 둘 두고 app.like.strategy로 고른다.
 * <ul>
 *   <li>redis(기본): {@link RedisRecipeLikeService} - 카운트를 Redis에서 원자적으로 모아 주기적으로 DB에 반영</li>
 *   <li>lock: {@link PessimisticLockRecipeLikeService} - 레시피 행에 비관적 락을 잡고 DB 카운트를 바로 갱신</li>
 * </ul>
 * 두 방식 모두 사용자당 1회 제약은 recipe_like 유니크 제약이 보장한다.
 */
public interface RecipeLikeService {

    void like(Long userId, Long recipeId);

    void unlike(Long userId, Long recipeId);
}
