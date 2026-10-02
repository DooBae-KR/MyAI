package com.personal.ai.data.codingtest;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "CODING_LECTURE")
public class CodingLecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "PROBLEM_ID")
    private CodingProblem problem;

    @Column(nullable = false, length = 20)
    private String promptVersion;

    /** 검증을 통과한 특강 JSON(가정, 17단계 본문). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String content;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CodingLecture() {}

    public CodingLecture(CodingProblem problem, String promptVersion, String content) {
        this.problem = problem;
        this.promptVersion = promptVersion;
        this.content = content;
    }

    public Long getId() { return id; }
    public CodingProblem getProblem() { return problem; }
    public String getPromptVersion() { return promptVersion; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
