package com.whattocook.ingredient.service;

import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.dto.IngredientAutocompleteResponse;
import com.whattocook.ingredient.repository.IngredientRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis Sorted Set(모든 score 0)의 사전순 범위 조회(ZRANGEBYLEX)로 접두어 자동완성을 한다.
 * 멤버 형식: "{정규화 별칭}\0{재료 id}\0{표준 이름}" → 별칭으로 찾고 표준 재료로 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class IngredientAutocompleteService {

    static final String KEY = "ingredient:autocomplete";
    static final int MAX_RESULTS = 10;
    // 한 재료의 여러 별칭이 같은 접두어에 걸릴 수 있어 넉넉히 읽고 재료 단위로 중복을 없앤다.
    private static final int SCAN_LIMIT = MAX_RESULTS * 5;
    private static final int MAX_ALIAS_LENGTH = 50;
    private static final String SEPARATOR = "\0";

    private final StringRedisTemplate redisTemplate;
    private final IngredientRepository ingredientRepository;

    public List<IngredientAutocompleteResponse> autocomplete(String query) {
        String prefix = Ingredient.normalize(query);
        if (prefix.isEmpty() || prefix.length() > MAX_ALIAS_LENGTH) {
            return List.of();
        }
        // shortcut: 상한 ￿는 BMP 문자까지만 덮는다(이모지 등 4바이트 문자 별칭은 누락), 그런 별칭이 생기면 바이트 단위 상한으로 바꾼다.
        Set<String> members = redisTemplate.opsForZSet().rangeByLex(KEY,
                Range.closed(prefix, prefix + Character.MAX_VALUE), Limit.limit().count(SCAN_LIMIT));
        if (members == null) {
            return List.of();
        }

        Map<Long, IngredientAutocompleteResponse> byId = new LinkedHashMap<>();
        for (String member : members) {
            String[] parts = member.split(SEPARATOR, 3);
            Long id = Long.valueOf(parts[1]);
            byId.putIfAbsent(id, new IngredientAutocompleteResponse(id, parts[2]));
            if (byId.size() == MAX_RESULTS) {
                break;
            }
        }
        return List.copyOf(byId.values());
    }

    /** 요청한 ID 순서대로 표준 재료를 준다. 없는 ID는 뺀다. */
    @Transactional(readOnly = true)
    public List<IngredientAutocompleteResponse> findByIds(List<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        for (Ingredient ingredient : ingredientRepository.findAllById(ids)) {
            names.put(ingredient.getId(), ingredient.getName());
        }
        return ids.stream().distinct().filter(names::containsKey)
                .map(id -> new IngredientAutocompleteResponse(id, names.get(id)))
                .toList();
    }

    /** 재료 마스터 전체로 색인을 다시 만든다. 임시 키에 채운 뒤 RENAME해 조회 중 빈 결과가 나오지 않게 한다. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void rebuildIndex() {
        Set<TypedTuple<String>> members = new HashSet<>();
        for (Ingredient ingredient : ingredientRepository.findAllWithAliases()) {
            for (String alias : ingredient.getAliases()) {
                members.add(TypedTuple.of(alias + SEPARATOR + ingredient.getId() + SEPARATOR + ingredient.getName(), 0.0));
            }
        }
        if (members.isEmpty()) {
            redisTemplate.delete(KEY);
            return;
        }
        String tmpKey = KEY + ":tmp";
        redisTemplate.delete(tmpKey);
        redisTemplate.opsForZSet().add(tmpKey, members);
        redisTemplate.rename(tmpKey, KEY);
    }
}
