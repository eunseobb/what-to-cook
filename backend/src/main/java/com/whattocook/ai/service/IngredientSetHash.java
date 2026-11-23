package com.whattocook.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * AI 레시피 재사용 키. 표준 재료 id 집합(별칭 정규화 후)을 중복 제거·정렬해 SHA-256 hex(64자)로 만든다.
 * 입력 순서나 별칭이 달라도 같은 재료 집합이면 같은 해시가 나온다.
 */
public final class IngredientSetHash {

    private IngredientSetHash() {
    }

    public static String of(Collection<Long> ingredientIds) {
        String canonical = ingredientIds.stream().distinct().sorted()
                .map(String::valueOf).collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }
}
