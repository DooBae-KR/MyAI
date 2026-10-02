package com.personal.ai.data.learning;

import com.personal.ai.core.learning.AssessmentType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "LEARNING_ASSESSMENT")
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "GOAL_ID")
    private LearningGoal goal;

    /** DIAGNOSTIC이면 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STEP_ID")
    private LearningStep step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentType type;

    /** 검증을 통과한 JSON 문자열. LLM 응답 원문을 그대로 넣지 않는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String questions;

    /** 채점 결과 JSON. null이면 아직 채점 전. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String result;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Assessment() {}

    public Assessment(LearningGoal goal, LearningStep step, AssessmentType type, String questions) {
        this.goal = goal;
        this.step = step;
        this.type = type;
        this.questions = questions;
    }

    public Long getId() { return id; }
    public LearningGoal getGoal() { return goal; }
    public LearningStep getStep() { return step; }
    public AssessmentType getType() { return type; }
    public String getQuestions() { return questions; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
