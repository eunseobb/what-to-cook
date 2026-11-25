package com.whattocook.recommendation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 재료 기반 추천 정렬 설정. 점수 = 일치율 × matchWeight + 취향 점수 × tasteWeight + 인기도 × popularityWeight.
 * 세 점수는 모두 0~1로 맞춘 뒤 더한다.
 *
 * @param matchWeight      재료 일치율 가중치 (w1)
 * @param tasteWeight      취향 점수 가중치 (w2)
 * @param popularityWeight 인기도 가중치 (w3)
 * @param candidateLimit   일치율 상위로 미리 추려 점수를 매길 후보 수
 */
@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(double matchWeight, double tasteWeight, double popularityWeight,
                                       int candidateLimit) {

    public RecommendationProperties {
        if (matchWeight < 0 || tasteWeight < 0 || popularityWeight < 0) {
            throw new IllegalArgumentException("app.recommendation 가중치는 0 이상이어야 합니다.");
        }
        if (candidateLimit < 1) {
            throw new IllegalArgumentException("app.recommendation.candidate-limit은 1 이상이어야 합니다: " + candidateLimit);
        }
    }
}
