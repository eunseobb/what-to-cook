-- 레시피 댓글. 대댓글은 1단계까지(parent_id는 최상위 댓글만 가리킨다, 서비스에서 검증).
-- 삭제는 deleted = TRUE로 소프트 삭제한다. 대댓글이 남은 삭제 댓글은 "삭제된 댓글"로 보여준다.
CREATE TABLE comment
(
    id         BIGSERIAL PRIMARY KEY,
    recipe_id  BIGINT       NOT NULL REFERENCES recipe (id),
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    parent_id  BIGINT REFERENCES comment (id),
    content    VARCHAR(500) NOT NULL,
    deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

-- 레시피별 최상위 댓글 커서 목록 (parent_id IS NULL, id 오름차순)
CREATE INDEX idx_comment_recipe_parent_id ON comment (recipe_id, parent_id, id);
-- 최상위 댓글 id 목록으로 대댓글 일괄 조회
CREATE INDEX idx_comment_parent_id ON comment (parent_id, id);
-- 작성자 FK
CREATE INDEX idx_comment_user_id ON comment (user_id);
