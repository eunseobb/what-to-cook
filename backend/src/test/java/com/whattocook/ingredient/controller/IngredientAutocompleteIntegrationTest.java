package com.whattocook.ingredient.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.ingredient.service.IngredientAutocompleteService;
import com.whattocook.support.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IngredientAutocompleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private IngredientAutocompleteService autocompleteService;

    @BeforeEach
    void setUp() {
        ingredientRepository.saveAll(List.of(
                Ingredient.builder().name("대파").category(IngredientCategory.VEGETABLE).baseUnit("g")
                        .aliases(List.of("파")).build(),
                Ingredient.builder().name("양파").category(IngredientCategory.VEGETABLE).baseUnit("g").build(),
                Ingredient.builder().name("돼지고기").category(IngredientCategory.MEAT).baseUnit("g").build()));
        autocompleteService.rebuildIndex();
    }

    @AfterEach
    void tearDown() {
        ingredientRepository.deleteAll();
        autocompleteService.rebuildIndex();
    }

    @Test
    @DisplayName("인증 없이 이름·별칭 접두어로 표준 재료를 찾는다")
    void autocompleteByNameAndAlias() throws Exception {
        mockMvc.perform(get("/api/ingredients/autocomplete").param("q", "파"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("대파"));

        mockMvc.perform(get("/api/ingredients/autocomplete").param("q", "돼 지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("돼지고기"));

        mockMvc.perform(get("/api/ingredients/autocomplete").param("q", "없는"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("인증 없이 재료 ID로 요청 순서대로 이름을 찾고, 없는 ID는 뺀다")
    void findByIds() throws Exception {
        List<Ingredient> saved = ingredientRepository.findAll();
        Ingredient first = saved.get(0);
        Ingredient last = saved.get(2);

        mockMvc.perform(get("/api/ingredients")
                        .param("id", String.valueOf(last.getId()), String.valueOf(first.getId()), "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(last.getId()))
                .andExpect(jsonPath("$[0].name").value(last.getName()))
                .andExpect(jsonPath("$[1].id").value(first.getId()));
    }

    @Test
    @DisplayName("q가 없으면 공통 에러 포맷으로 400을 응답한다")
    void missingQuery() throws Exception {
        mockMvc.perform(get("/api/ingredients/autocomplete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C003"));
    }
}
