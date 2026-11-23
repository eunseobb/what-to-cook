-- 사용자 선호 태그. 온보딩에서 고른 태그가 초기 가중치로 들어가고, 행동 반영(F3-2)이 가중치를 바꾼다.
-- (user_id, tag_id) 유니크 인덱스가 사용자별 조회도 맡는다.
CREATE TABLE user_preference_tag
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tag_id     BIGINT           NOT NULL REFERENCES tag (id),
    weight     DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP(6)     NOT NULL,
    updated_at TIMESTAMP(6)     NOT NULL,
    CONSTRAINT uk_user_preference_tag_user_tag UNIQUE (user_id, tag_id)
);

-- 알레르기·비선호 재료. 추천 시 하드 필터로 쓴다. 한 재료는 사유 하나만 가진다.
-- (user_id, ingredient_id) 유니크 인덱스가 사용자별 조회도 맡는다.
CREATE TABLE user_excluded_ingredient
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ingredient_id BIGINT       NOT NULL REFERENCES ingredient (id),
    type          VARCHAR(10)  NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_user_excluded_ingredient_user_ingredient UNIQUE (user_id, ingredient_id)
);
