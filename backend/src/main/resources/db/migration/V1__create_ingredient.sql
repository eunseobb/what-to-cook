-- 표준 재료 마스터
CREATE TABLE ingredient
(
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(50)  NOT NULL UNIQUE,
    category         VARCHAR(30)  NOT NULL,
    base_unit        VARCHAR(10)  NOT NULL,
    is_pantry_staple BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL
);

-- 별칭(정규화된 형태). 표준 이름의 정규화 형태도 함께 저장해 별칭 하나로만 조회한다.
-- 별칭은 전체 재료에서 유일하다: 하나의 입력이 두 재료로 매칭되지 않게 한다.
CREATE TABLE ingredient_alias
(
    ingredient_id BIGINT      NOT NULL REFERENCES ingredient (id) ON DELETE CASCADE,
    alias         VARCHAR(50) NOT NULL,
    PRIMARY KEY (ingredient_id, alias),
    CONSTRAINT uk_ingredient_alias_alias UNIQUE (alias)
);

-- 단위 환산: 1 unit = base_unit_amount (base_unit 기준). 예) 대파 1대 = 100g
CREATE TABLE ingredient_unit_conversion
(
    ingredient_id    BIGINT        NOT NULL REFERENCES ingredient (id) ON DELETE CASCADE,
    unit             VARCHAR(10)   NOT NULL,
    base_unit_amount NUMERIC(10, 2) NOT NULL CHECK (base_unit_amount > 0),
    PRIMARY KEY (ingredient_id, unit)
);
