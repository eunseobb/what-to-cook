-- 레시피·댓글 신고. 같은 사용자는 같은 대상을 한 번만 신고할 수 있다(신고 수 부풀리기 방지).
-- 누적 신고 수가 app.report.hide-threshold에 이르면 대상을 숨긴다(레시피는 status = 'HIDDEN', 댓글은 hidden = TRUE).
CREATE TABLE report
(
    id          BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT       NOT NULL REFERENCES users (id),
    target_type VARCHAR(20)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    updated_at  TIMESTAMP(6) NOT NULL,
    -- 신고자 FK 인덱스를 겸한다.
    CONSTRAINT uk_report_reporter_target UNIQUE (reporter_id, target_type, target_id)
);

-- 대상별 누적 신고 수 집계
CREATE INDEX idx_report_target ON report (target_type, target_id);

-- 신고 누적으로 숨긴 댓글. 대댓글이 남아 있으면 "숨겨진 댓글"로 보여준다.
ALTER TABLE comment
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
