package com.personal.ai.data.learning;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "LEARNING_ANSWER")
public class LearningAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ASSESSMENT_ID")
    private Assessment assessment;

    @Lob
    @Column(nullable = false)
    private String answerText;

    /** 평가 항목별 점수 JSON (correctness, conceptUnderstanding 등). 채점 전에는 null. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String scores;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LearningAnswer() {}

    public LearningAnswer(Assessment assessment, String answerText) {
        this.assessment = assessment;
        this.answerText = answerText;
    }

    public Long getId() { return id; }
    public Assessment getAssessment() { return assessment; }
    public String getAnswerText() { return answerText; }
    public String getScores() { return scores; }
    public void setScores(String scores) { this.scores = scores; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
