package com.personal.ai.data.learning;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "PATTERN_EVIDENCE")
public class PatternEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "PATTERN_ID")
    private ThinkingPattern pattern;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ANSWER_ID")
    private LearningAnswer answer;

    /** 답변에서 그대로 인용한 부분. 실제 답변에 있는 문장임을 서비스가 검증한 뒤 저장한다. */
    @Column(nullable = false, length = 1000)
    private String quote;

    @Column(length = 1000)
    private String note;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PatternEvidence() {}

    public PatternEvidence(ThinkingPattern pattern, LearningAnswer answer, String quote, String note) {
        this.pattern = pattern;
        this.answer = answer;
        this.quote = quote;
        this.note = note;
    }

    public Long getId() { return id; }
    public ThinkingPattern getPattern() { return pattern; }
    public LearningAnswer getAnswer() { return answer; }
    public String getQuote() { return quote; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
