-- 재료 가격 (기준 단위당 가격). 같은 재료·출처·기준 시각에는 한 건만 둔다. 배치가 하루 2회 돌므로 날짜가 아닌 시각으로 구분한다.
-- 유니크 인덱스가 재료별 가격 조회도 맡는다.
CREATE TABLE ingredient_price
(
    id                  BIGSERIAL PRIMARY KEY,
    ingredient_id       BIGINT         NOT NULL REFERENCES ingredient (id),
    price_per_base_unit NUMERIC(12, 4) NOT NULL CHECK (price_per_base_unit > 0),
    referenced_at       TIMESTAMP(6)   NOT NULL,
    source              VARCHAR(20)    NOT NULL,
    created_at          TIMESTAMP(6)   NOT NULL,
    updated_at          TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_ingredient_price_ingredient_source_referenced_at UNIQUE (ingredient_id, source, referenced_at)
);
