-- 사고 패턴의 근거를 코딩 풀이(제출 기록)에서도 받을 수 있게 한다.
-- 근거는 진단 답변(answer_id) 또는 코딩 제출(submission_id) 중 정확히 하나를 가리킨다.
ALTER TABLE coding_submission ADD COLUMN analyzed_at TIMESTAMP;
CREATE INDEX ix_coding_submission_analyzed ON coding_submission (analyzed_at);

ALTER TABLE pattern_evidence ALTER COLUMN answer_id DROP NOT NULL;
ALTER TABLE pattern_evidence ADD COLUMN submission_id BIGINT REFERENCES coding_submission (id);
ALTER TABLE pattern_evidence ADD CONSTRAINT ck_pattern_evidence_one_source
    CHECK ((answer_id IS NOT NULL) <> (submission_id IS NOT NULL));
ALTER TABLE pattern_evidence ADD CONSTRAINT uk_pattern_evidence_submission UNIQUE (pattern_id, submission_id);
CREATE INDEX ix_pattern_evidence_submission ON pattern_evidence (submission_id);
