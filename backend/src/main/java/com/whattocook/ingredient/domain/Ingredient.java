package com.whattocook.ingredient.domain;

import com.whattocook.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 표준 재료. 사용자 입력("파", "대 파")은 별칭으로 이 재료에 매핑된다.
 */
@Getter
@Entity
@Table(name = "ingredient")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IngredientCategory category;

    /** 가격·분량 계산의 기준 단위 (예: g, ml, 개) */
    @Column(name = "base_unit", nullable = false, length = 10)
    private String baseUnit;

    /** 기본 양념 여부. 재료 일치율 계산에서 제외한다. */
    @Column(name = "is_pantry_staple", nullable = false)
    private boolean pantryStaple;

    /** 정규화된 별칭. 표준 이름의 정규화 형태를 항상 포함한다. */
    @ElementCollection
    @CollectionTable(name = "ingredient_alias", joinColumns = @JoinColumn(name = "ingredient_id"))
    @Column(name = "alias", nullable = false, length = 50)
    private Set<String> aliases = new HashSet<>();

    /** 단위 → 기준 단위 환산량 (예: "대" → 100 이면 1대 = 100g) */
    @ElementCollection
    @CollectionTable(name = "ingredient_unit_conversion", joinColumns = @JoinColumn(name = "ingredient_id"))
    @MapKeyColumn(name = "unit", length = 10)
    @Column(name = "base_unit_amount", nullable = false, precision = 10, scale = 2)
    private Map<String, BigDecimal> unitConversions = new HashMap<>();

    @Builder
    private Ingredient(String name, IngredientCategory category, String baseUnit, boolean pantryStaple,
                       Collection<String> aliases, Map<String, BigDecimal> unitConversions) {
        this.name = name.strip();
        this.category = category;
        this.baseUnit = baseUnit;
        this.pantryStaple = pantryStaple;
        this.aliases.add(normalize(name));
        if (aliases != null) {
            aliases.stream().map(Ingredient::normalize).filter(a -> !a.isEmpty()).forEach(this.aliases::add);
        }
        if (unitConversions != null) {
            this.unitConversions.putAll(unitConversions);
        }
    }

    /** 매칭용 정규화: 공백 제거 + 소문자. 별칭 저장과 입력 조회에 같은 규칙을 쓴다. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
