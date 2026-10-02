CREATE TABLE learning_subject (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_learning_subject_name UNIQUE (name)
);

CREATE TABLE learning_subject_prereq (
    subject_id BIGINT NOT NULL REFERENCES learning_subject (id),
    prereq_id  BIGINT NOT NULL REFERENCES learning_subject (id),
    PRIMARY KEY (subject_id, prereq_id)
);

CREATE TABLE learning_goal (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_id    BIGINT NOT NULL REFERENCES learning_subject (id),
    goal_text     VARCHAR(1000) NOT NULL,
    target_level  VARCHAR(20),
    current_level VARCHAR(20),
    deadline      DATE,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE learning_step (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    goal_id    BIGINT NOT NULL REFERENCES learning_goal (id),
    seq        INTEGER NOT NULL,
    title      VARCHAR(200) NOT NULL,
    objective  VARCHAR(2000),
    difficulty INTEGER,
    status     VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_learning_step_seq UNIQUE (goal_id, seq)
);

CREATE TABLE learning_assessment (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    goal_id    BIGINT NOT NULL REFERENCES learning_goal (id),
    step_id    BIGINT REFERENCES learning_step (id),
    type       VARCHAR(20) NOT NULL,
    questions  JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE learning_answer (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assessment_id BIGINT NOT NULL REFERENCES learning_assessment (id),
    answer_text   TEXT NOT NULL,
    scores        JSONB,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX ix_learning_goal_subject ON learning_goal (subject_id);
CREATE INDEX ix_learning_assessment_goal ON learning_assessment (goal_id);
CREATE INDEX ix_learning_assessment_step ON learning_assessment (step_id);
CREATE INDEX ix_learning_answer_assessment ON learning_answer (assessment_id);
