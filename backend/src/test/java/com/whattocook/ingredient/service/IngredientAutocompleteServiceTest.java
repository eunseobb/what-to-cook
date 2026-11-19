package com.whattocook.ingredient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.whattocook.ingredient.dto.IngredientAutocompleteResponse;
import com.whattocook.ingredient.repository.IngredientRepository;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class IngredientAutocompleteServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private IngredientAutocompleteService autocompleteService;

    @Test
    @DisplayName("여러 별칭이 걸린 같은 재료는 한 번만, 사전순으로 돌려준다")
    void dedupesByIngredient() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOps);
        given(zSetOps.rangeByLex(eq(IngredientAutocompleteService.KEY), any(Range.class), any(Limit.class)))
                .willReturn(new LinkedHashSet<>(List.of("파\u00001\u0000대파", "파뿌리\u00002\u0000파뿌리", "파채\u00001\u0000대파")));

        List<IngredientAutocompleteResponse> result = autocompleteService.autocomplete(" 파");

        assertThat(result).containsExactly(
                new IngredientAutocompleteResponse(1L, "대파"),
                new IngredientAutocompleteResponse(2L, "파뿌리"));
    }

    @Test
    @DisplayName("빈 입력이나 별칭 최대 길이를 넘는 입력은 Redis를 조회하지 않는다")
    void blankOrTooLongSkipsRedis() {
        assertThat(autocompleteService.autocomplete("  ")).isEmpty();
        assertThat(autocompleteService.autocomplete("가".repeat(51))).isEmpty();
        verifyNoInteractions(redisTemplate);
    }
}
