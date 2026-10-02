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

    /** 진단 문제 번호(quiz.questions[].id). */
    private Integer questionId;

    @Column(nullable = false)
    private String answerText;

    /** 평가 항목별 점수 JSON (correctness, conceptUnderstanding 등). 채점 전에는 null. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String scores;

    /** 사고 패턴 분석에 쓰인 시각. null이면 아직 분석 전. */
    private LocalDateTime analyzedAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LearningAnswer() {}

    public LearningAnswer(Assessment assessment, Integer questionId, String answerText) {
        this.assessment = assessment;
        this.questionId = questionId;
        this.answerText = answerText;
    }

    public Long getId() { return id; }
    public Assessment getAssessment() { return assessment; }
    public Integer getQuestionId() { return questionId; }
    public String getAnswerText() { return answerText; }
    public String getScores() { return scores; }
    public void setScores(String scores) { this.scores = scores; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void markAnalyzed() { this.analyzedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
