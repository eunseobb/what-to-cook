package com.whattocook.global.common;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 기반 페이지네이션 공통 응답.
 * 조회 시 size + 1건을 가져와 다음 페이지 존재 여부를 판단한다.
 *
 * @param items      현재 페이지 항목
 * @param nextCursor 다음 페이지 요청에 쓸 커서 (마지막 페이지면 null)
 * @param hasNext    다음 페이지 존재 여부
 */
public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasNext) {

    /**
     * @param fetched         size + 1건으로 조회한 결과
     * @param size            요청한 페이지 크기
     * @param cursorExtractor 마지막 항목에서 커서를 뽑는 함수
     */
    public static <T> CursorPageResponse<T> of(List<T> fetched, int size, Function<T, String> cursorExtractor) {
        boolean hasNext = fetched.size() > size;
        List<T> items = hasNext ? List.copyOf(fetched.subList(0, size)) : List.copyOf(fetched);
        String nextCursor = hasNext && !items.isEmpty() ? cursorExtractor.apply(items.get(items.size() - 1)) : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }
}
