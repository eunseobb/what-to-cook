package com.whattocook.ingredient.controller;

import com.whattocook.ingredient.dto.IngredientAutocompleteResponse;
import com.whattocook.ingredient.service.IngredientAutocompleteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ingredient", description = "재료")
@RestController
@RequestMapping("/api/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientAutocompleteService autocompleteService;

    @Operation(summary = "재료 자동완성", description = "이름·별칭의 접두어로 표준 재료를 최대 10개 찾는다. 공백·대소문자는 무시한다.")
    @GetMapping("/autocomplete")
    public List<IngredientAutocompleteResponse> autocomplete(@RequestParam String q) {
        return autocompleteService.autocomplete(q);
    }
}
