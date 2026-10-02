-- 코딩테스트: 문제는 원문이 아니라 메타데이터와 사용자가 직접 쓴 요약만 저장한다(원문은 원 출처에서 확인).
CREATE TABLE coding_problem (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source              VARCHAR(50),
    source_url          VARCHAR(500),
    external_id         VARCHAR(100),
    company             VARCHAR(100),
    title               VARCHAR(200) NOT NULL,
    difficulty          VARCHAR(30),
    category            VARCHAR(100),
    algorithm           VARCHAR(200),
    data_structure      VARCHAR(200),
    language            VARCHAR(30),
    description_summary VARCHAR(2000),
    estimated_time      INTEGER,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE coding_lecture (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    problem_id     BIGINT NOT NULL REFERENCES coding_problem (id),
    prompt_version VARCHAR(20) NOT NULL,
    content        JSONB NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX ix_coding_lecture_problem ON coding_lecture (problem_id);

CREATE TABLE coding_submission (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    problem_id  BIGINT NOT NULL REFERENCES coding_problem (id),
    language    VARCHAR(30) NOT NULL,
    code        TEXT NOT NULL,
    explanation VARCHAR(4000),
    review      JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX ix_coding_submission_problem ON coding_submission (problem_id);
