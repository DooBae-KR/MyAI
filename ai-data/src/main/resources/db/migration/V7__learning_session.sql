-- 학습 시간: Step을 공부한 시간(학습 화면을 열어 두고 탭이 보이는 동안)을 Step·날짜별로 합산한다.
-- 행이 하루에 하나만 늘도록 (step, 날짜)를 유일하게 둔다.
CREATE TABLE learning_session (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    step_id    BIGINT NOT NULL REFERENCES learning_step (id),
    study_date DATE NOT NULL,
    seconds    INTEGER NOT NULL CHECK (seconds >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_learning_session_step_date UNIQUE (step_id, study_date)
);
CREATE INDEX ix_learning_session_date ON learning_session (study_date);
