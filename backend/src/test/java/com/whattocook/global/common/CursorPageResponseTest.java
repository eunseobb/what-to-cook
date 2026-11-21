package com.whattocook.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorPageResponseTest {

    @Test
    @DisplayName("size보다 많이 조회되면 size만큼 자르고 마지막 항목으로 다음 커서를 만든다")
    void hasNext() {
        CursorPageResponse<Long> page = CursorPageResponse.of(List.of(10L, 9L, 8L), 2, String::valueOf);

        assertThat(page.items()).containsExactly(10L, 9L);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("9");
    }

    @Test
    @DisplayName("size 이하로 조회되면 마지막 페이지다")
    void lastPage() {
        CursorPageResponse<Long> page = CursorPageResponse.of(List.of(2L, 1L), 2, String::valueOf);

        assertThat(page.items()).containsExactly(2L, 1L);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("빈 결과는 다음 페이지가 없다")
    void empty() {
        CursorPageResponse<Long> page = CursorPageResponse.of(List.of(), 20, String::valueOf);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }
}
