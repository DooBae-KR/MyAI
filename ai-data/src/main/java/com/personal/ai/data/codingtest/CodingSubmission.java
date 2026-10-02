package com.personal.ai.data.codingtest;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "CODING_SUBMISSION")
public class CodingSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_ID")
    private CodingProblem problem;

    @Column(nullable = false, length = 30)
    private String language;

    @Column(nullable = false)
    private String code;

    @Column(length = 4000)
    private String explanation;

    /** 검증을 통과한 풀이 비교 JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String review;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CodingSubmission() {}

    public CodingSubmission(CodingProblem problem, String language, String code, String explanation, String review) {
        this.problem = problem;
        this.language = language;
        this.code = code;
        this.explanation = explanation;
        this.review = review;
    }

    public Long getId() { return id; }
    public CodingProblem getProblem() { return problem; }
    public String getLanguage() { return language; }
    public String getCode() { return code; }
    public String getExplanation() { return explanation; }
    public String getReview() { return review; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
