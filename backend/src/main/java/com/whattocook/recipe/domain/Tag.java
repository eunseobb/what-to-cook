package com.whattocook.recipe.domain;

import com.whattocook.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 태그 마스터. (type, name)으로 유일하다. 레시피 태그와 사용자 선호 태그가 함께 참조한다.
 */
@Getter
@Entity
@Table(name = "tag")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagType type;

    @Column(nullable = false, length = 30)
    private String name;

    /** 이미지 없는 카드의 플레이스홀더 색. 색값이 아니라 프론트 디자인 토큰 이름이다(accent-sage → --color-accent-sage). */
    @Column(name = "color_key", nullable = false, length = 30)
    private String colorKey = "accent-sage";

    public Tag(TagType type, String name) {
        this.type = type;
        this.name = name.strip();
    }
}
