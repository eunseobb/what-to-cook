package com.whattocook.user.domain;

import com.whattocook.global.common.BaseTimeEntity;
import com.whattocook.recipe.domain.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 선호 태그와 가중치. 취향 점수는 이 가중치와 레시피 태그의 가중합이다.
 * 온보딩에서 고른 태그는 초기 가중치로 들어가고, 행동 반영(F3-2)이 가중치를 바꾼다.
 */
@Getter
@Entity
@Table(name = "user_preference_tag")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferenceTag extends BaseTimeEntity {

    /** 온보딩에서 고른 태그의 초기 가중치 */
    public static final double ONBOARDING_WEIGHT = 1.0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(nullable = false)
    private double weight;

    public UserPreferenceTag(User user, Tag tag, double weight) {
        this.user = user;
        this.tag = tag;
        this.weight = weight;
    }
}
