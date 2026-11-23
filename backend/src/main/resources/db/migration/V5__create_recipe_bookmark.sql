-- 레시피 저장(북마크). 사용자당 레시피 1회만 저장된다.
CREATE TABLE recipe_bookmark
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    recipe_id  BIGINT       NOT NULL REFERENCES recipe (id),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_recipe_bookmark_user_recipe UNIQUE (user_id, recipe_id)
);

-- 레시피 FK. 마이페이지 저장 목록은 유니크 제약의 (user_id, recipe_id) 인덱스를 쓴다.
CREATE INDEX idx_recipe_bookmark_recipe_id ON recipe_bookmark (recipe_id);
