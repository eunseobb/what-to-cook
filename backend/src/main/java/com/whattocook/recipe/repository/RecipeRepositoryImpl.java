package com.whattocook.recipe.repository;

import static com.whattocook.recipe.domain.QRecipe.recipe;
import static com.whattocook.user.domain.QUserExcludedIngredient.userExcludedIngredient;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.recipe.domain.RecipeSort;
import com.whattocook.recipe.domain.RecipeStatus;
import com.whattocook.user.domain.ExclusionType;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RecipeRepositoryImpl implements RecipeRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Recipe> search(RecipeListCondition condition, RecipeCursor cursor, int limit) {
        BooleanBuilder where = new BooleanBuilder(recipe.status.eq(RecipeStatus.PUBLISHED));
        // 태그마다 EXISTS 서브쿼리를 붙여 선택한 태그를 모두 가진 레시피만 남긴다.
        condition.tagIds().forEach(tagId -> where.and(recipe.tags.any().id.eq(tagId)));
        if (condition.createdAfter() != null) {
            where.and(recipe.createdAt.goe(condition.createdAfter()));
        }
        if (condition.source() != null) {
            where.and(recipe.source.eq(condition.source()));
        }
        if (condition.allergyUserId() != null) {
            // 알레르기 재료가 하나라도 든 레시피를 뺀다. 비선호(DISLIKE)는 하드 필터에 쓰지 않는다.
            where.and(JPAExpressions.selectOne().from(userExcludedIngredient)
                    .where(userExcludedIngredient.user.id.eq(condition.allergyUserId()),
                            userExcludedIngredient.type.eq(ExclusionType.ALLERGY),
                            recipe.ingredients.any().ingredient.eq(userExcludedIngredient.ingredient))
                    .notExists());
        }
        if (cursor != null) {
            where.and(after(cursor, condition.sort()));
        }
        return queryFactory.selectFrom(recipe)
                .leftJoin(recipe.author).fetchJoin()
                .where(where)
                .orderBy(orderBy(condition.sort()))
                .limit(limit)
                .fetch();
    }

    private static OrderSpecifier<?>[] orderBy(RecipeSort sort) {
        if (sort == RecipeSort.POPULAR) {
            // idx_recipe_like_count_created_at(like_count, created_at)를 역방향으로 탄다.
            return new OrderSpecifier<?>[]{recipe.likeCount.desc(), recipe.createdAt.desc(), recipe.id.desc()};
        }
        return new OrderSpecifier<?>[]{recipe.id.desc()};
    }

    /** 정렬 순서상 커서 다음에 오는 행. (likeCount, createdAt, id) < 커서 값을 풀어 쓴다. */
    private static BooleanExpression after(RecipeCursor cursor, RecipeSort sort) {
        if (sort != RecipeSort.POPULAR) {
            return recipe.id.lt(cursor.id());
        }
        return recipe.likeCount.lt(cursor.likeCount())
                .or(recipe.likeCount.eq(cursor.likeCount()).and(recipe.createdAt.lt(cursor.createdAt())))
                .or(recipe.likeCount.eq(cursor.likeCount()).and(recipe.createdAt.eq(cursor.createdAt()))
                        .and(recipe.id.lt(cursor.id())));
    }
}
