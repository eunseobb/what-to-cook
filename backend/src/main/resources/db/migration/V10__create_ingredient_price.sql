-- 재료 가격 (기준 단위당 가격). 같은 재료·출처·기준일에는 한 건만 둔다.
-- 유니크 인덱스가 재료별 가격 조회도 맡는다.
CREATE TABLE ingredient_price
(
    id                  BIGSERIAL PRIMARY KEY,
    ingredient_id       BIGINT         NOT NULL REFERENCES ingredient (id),
    price_per_base_unit NUMERIC(12, 4) NOT NULL CHECK (price_per_base_unit > 0),
    reference_date      DATE           NOT NULL,
    source              VARCHAR(20)    NOT NULL,
    created_at          TIMESTAMP(6)   NOT NULL,
    updated_at          TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_ingredient_price_ingredient_source_date UNIQUE (ingredient_id, source, reference_date)
);
