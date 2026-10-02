-- 사고 패턴(가설)과 그 근거. 패턴은 사용자의 능력이나 성격이 아니라 답변에서 관찰된 행동의 기록이다.
ALTER TABLE learning_answer ADD COLUMN analyzed_at TIMESTAMP;

CREATE TABLE thinking_pattern (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    description          VARCHAR(1000),
    evidence_count       INTEGER NOT NULL DEFAULT 0,
    confidence           NUMERIC(3, 2) NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL,
    first_observed_at    TIMESTAMP NOT NULL DEFAULT now(),
    last_observed_at     TIMESTAMP NOT NULL DEFAULT now(),
    improvement_strategy VARCHAR(1000)
);
CREATE UNIQUE INDEX uk_thinking_pattern_name ON thinking_pattern (lower(name));

CREATE TABLE pattern_evidence (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pattern_id BIGINT NOT NULL REFERENCES thinking_pattern (id),
    answer_id  BIGINT NOT NULL REFERENCES learning_answer (id),
    quote      VARCHAR(1000) NOT NULL,
    note       VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_pattern_evidence UNIQUE (pattern_id, answer_id)
);
CREATE INDEX ix_pattern_evidence_pattern ON pattern_evidence (pattern_id);
CREATE INDEX ix_learning_answer_analyzed ON learning_answer (analyzed_at);
