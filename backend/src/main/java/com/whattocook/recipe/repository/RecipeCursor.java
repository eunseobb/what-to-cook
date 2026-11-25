package com.whattocook.recipe.repository;

import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.recipe.domain.RecipeSort;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 목록 커서. 최신순은 id만, 인기순은 (likeCount, createdAt, id)를 쓴다.
 * 인기순은 좋아요 수가 같은 레시피가 많아 정렬 키 전체를 커서에 담아야 페이지 사이 중복·누락이 없다.
 * 문자열 형식: 최신순 {@code "id"}, 인기순 {@code "likeCount_createdAt_id"}.
 */
public record RecipeCursor(int likeCount, LocalDateTime createdAt, long id) {

    public String encode(RecipeSort sort) {
        return sort == RecipeSort.POPULAR ? likeCount + "_" + createdAt + "_" + id : String.valueOf(id);
    }

    /** @return 첫 페이지(cursor가 null)면 null */
    public static RecipeCursor decode(String cursor, RecipeSort sort) {
        if (cursor == null) {
            return null;
        }
        try {
            if (sort != RecipeSort.POPULAR) {
                return new RecipeCursor(0, null, Long.parseLong(cursor));
            }
            String[] parts = cursor.split("_");
            if (parts.length != 3) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return new RecipeCursor(Integer.parseInt(parts[0]), LocalDateTime.parse(parts[1]),
                    Long.parseLong(parts[2]));
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
