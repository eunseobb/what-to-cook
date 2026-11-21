package com.whattocook.ingredient.service;

import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.repository.IngredientRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 입력한 재료명을 별칭을 통해 표준 재료로 매핑한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientNormalizationService {

    // 매칭 실패 입력만 모으는 전용 로거. 로그 수집 후 마스터 데이터(별칭) 보강에 쓴다.
    private static final Logger UNMATCHED_LOG = LoggerFactory.getLogger("ingredient.unmatched");

    private final IngredientRepository ingredientRepository;

    public IngredientNormalizationResult normalize(Collection<String> inputs) {
        Map<String, String> rawByKey = new LinkedHashMap<>();
        for (String raw : inputs) {
            String key = Ingredient.normalize(raw);
            if (!key.isEmpty()) {
                rawByKey.putIfAbsent(key, raw.strip());
            }
        }
        if (rawByKey.isEmpty()) {
            return new IngredientNormalizationResult(List.of(), List.of());
        }

        Map<String, Ingredient> ingredientByAlias = new HashMap<>();
        for (Ingredient ingredient : ingredientRepository.findAllByAliasIn(rawByKey.keySet())) {
            ingredient.getAliases().forEach(alias -> ingredientByAlias.put(alias, ingredient));
        }

        LinkedHashSet<Ingredient> matched = new LinkedHashSet<>();
        List<String> unmatched = new ArrayList<>();
        rawByKey.forEach((key, raw) -> {
            Ingredient ingredient = ingredientByAlias.get(key);
            if (ingredient != null) {
                matched.add(ingredient);
            } else {
                unmatched.add(raw);
                UNMATCHED_LOG.info("unmatched ingredient input: {}", raw);
            }
        });
        return new IngredientNormalizationResult(List.copyOf(matched), List.copyOf(unmatched));
    }
}
