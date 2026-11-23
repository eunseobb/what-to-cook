package com.whattocook.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngredientSetHashTest {

    @Test
    @DisplayName("순서·중복과 상관없이 같은 재료 집합이면 같은 64자 해시를 만든다")
    void sameSetSameHash() {
        String hash = IngredientSetHash.of(List.of(3L, 1L, 2L));

        assertThat(hash).hasSize(64).isEqualTo(IngredientSetHash.of(List.of(1L, 2L, 3L, 2L)));
        assertThat(IngredientSetHash.of(List.of(1L, 2L))).isNotEqualTo(hash);
        assertThat(IngredientSetHash.of(List.of(10L, 2L))).isEqualTo(IngredientSetHash.of(List.of(2L, 10L)));
    }
}
