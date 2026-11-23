-- 레시피 좋아요. 사용자당 레시피 하나에 1회만 가능하다.
CREATE TABLE recipe_like
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    recipe_id  BIGINT       NOT NULL REFERENCES recipe (id),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_recipe_like_user_recipe UNIQUE (user_id, recipe_id)
);

-- 레시피 FK. (user_id, recipe_id) 유니크 인덱스가 마이페이지 "좋아요한 레시피" 조회를 맡는다.
CREATE INDEX idx_recipe_like_recipe_id ON recipe_like (recipe_id);
