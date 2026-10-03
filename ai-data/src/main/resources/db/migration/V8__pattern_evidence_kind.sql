-- 근거 종류: 패턴이 관찰된 근거(OBSERVED)와 개선 훈련 후의 개선 신호(IMPROVED). 기존 근거는 모두 OBSERVED다.
ALTER TABLE pattern_evidence ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'OBSERVED'
    CONSTRAINT ck_pattern_evidence_kind CHECK (kind IN ('OBSERVED', 'IMPROVED'));
