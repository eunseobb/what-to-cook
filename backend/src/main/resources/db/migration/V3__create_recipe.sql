-- 레시피 (커뮤니티 + AI 생성). 삭제는 status = 'DELETED'로 소프트 삭제한다.
CREATE TABLE recipe
(
    id                  BIGSERIAL PRIMARY KEY,
    author_id           BIGINT REFERENCES users (id),
    source              VARCHAR(20)  NOT NULL,
    ingredient_set_hash VARCHAR(64),
    title               VARCHAR(100) NOT NULL,
    main_image_url      VARCHAR(500),
    difficulty          VARCHAR(10)  NOT NULL,
    cook_time_minutes   INT          NOT NULL CHECK (cook_time_minutes > 0),
    servings            INT          NOT NULL CHECK (servings > 0),
    like_count          INT          NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    updated_at          TIMESTAMP(6) NOT NULL
);

-- AI 레시피 재사용(캐시 히트) 조회
CREATE INDEX idx_recipe_ingredient_set_hash ON recipe (ingredient_set_hash);
-- 인기 레시피 목록
CREATE INDEX idx_recipe_like_count_created_at ON recipe (like_count, created_at);
-- 작성자 FK, 마이페이지 내 레시피
CREATE INDEX idx_recipe_author_id ON recipe (author_id);

-- 레시피 재료. 순서 컬럼으로 표시 순서를 지킨다.
CREATE TABLE recipe_ingredient
(
    recipe_id        BIGINT NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    ingredient_order INT    NOT NULL,
    ingredient_id    BIGINT NOT NULL REFERENCES ingredient (id),
    quantity         NUMERIC(10, 2) CHECK (quantity > 0),
    unit             VARCHAR(10),
    PRIMARY KEY (recipe_id, ingredient_order)
);

-- 재료 기반 레시피 검색: 재료 → 레시피
CREATE INDEX idx_recipe_ingredient_ingredient_recipe ON recipe_ingredient (ingredient_id, recipe_id);

CREATE TABLE recipe_step
(
    recipe_id  BIGINT        NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    step_order INT           NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    image_url  VARCHAR(500),
    PRIMARY KEY (recipe_id, step_order)
);

-- 태그 마스터. 레시피 태그와 온보딩 선호 태그가 함께 쓴다.
CREATE TABLE tag
(
    id         BIGSERIAL PRIMARY KEY,
    type       VARCHAR(20)  NOT NULL,
    name       VARCHAR(30)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_tag_type_name UNIQUE (type, name)
);

CREATE TABLE recipe_tag
(
    recipe_id BIGINT NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    tag_id    BIGINT NOT NULL REFERENCES tag (id),
    PRIMARY KEY (recipe_id, tag_id)
);

-- 태그 → 레시피 조회 (취향 점수 계산)
CREATE INDEX idx_recipe_tag_tag_id ON recipe_tag (tag_id);

-- 기본 태그 시드: 레시피 태그와 온보딩 선호 태그 선택지로 함께 쓴다.
INSERT INTO tag (type, name, created_at, updated_at)
VALUES ('CUISINE', '한식', NOW(), NOW()),
       ('CUISINE', '중식', NOW(), NOW()),
       ('CUISINE', '일식', NOW(), NOW()),
       ('CUISINE', '양식', NOW(), NOW()),
       ('CUISINE', '분식', NOW(), NOW()),
       ('CUISINE', '디저트', NOW(), NOW()),
       ('TASTE', '매운맛', NOW(), NOW()),
       ('TASTE', '단맛', NOW(), NOW()),
       ('TASTE', '짠맛', NOW(), NOW()),
       ('TASTE', '담백한맛', NOW(), NOW()),
       ('TASTE', '새콤한맛', NOW(), NOW()),
       ('PURPOSE', '다이어트', NOW(), NOW()),
       ('PURPOSE', '간편식', NOW(), NOW()),
       ('PURPOSE', '야식', NOW(), NOW()),
       ('PURPOSE', '손님 접대', NOW(), NOW()),
       ('PURPOSE', '도시락', NOW(), NOW()),
       ('DIFFICULTY', '초보', NOW(), NOW()),
       ('DIFFICULTY', '중급', NOW(), NOW()),
       ('DIFFICULTY', '고급', NOW(), NOW());
