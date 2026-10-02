package com.personal.ai.data.learning;

import com.personal.ai.core.learning.PatternStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "THINKING_PATTERN")
public class ThinkingPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    private int evidenceCount;

    /** 0.00~1.00. DB 컬럼 NUMERIC(3,2)와 맞추려고 BigDecimal로 매핑한다. */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatternStatus status;

    @Column(nullable = false)
    private LocalDateTime firstObservedAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime lastObservedAt = LocalDateTime.now();

    @Column(length = 1000)
    private String improvementStrategy;

    protected ThinkingPattern() {}

    public ThinkingPattern(String name, String description, String improvementStrategy) {
        this.name = name;
        this.description = description;
        this.improvementStrategy = improvementStrategy;
        this.status = PatternStatus.HYPOTHESIS;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getEvidenceCount() { return evidenceCount; }
    public double getConfidence() { return confidence.doubleValue(); }
    public PatternStatus getStatus() { return status; }
    public LocalDateTime getFirstObservedAt() { return firstObservedAt; }
    public LocalDateTime getLastObservedAt() { return lastObservedAt; }
    public String getImprovementStrategy() { return improvementStrategy; }

    public void setStatus(PatternStatus status) { this.status = status; }
    public void setImprovementStrategy(String improvementStrategy) { this.improvementStrategy = improvementStrategy; }
    public void setDescription(String description) { this.description = description; }
    public void observedNow() { this.lastObservedAt = LocalDateTime.now(); }

    /** 근거 개수와 신뢰도는 서비스가 계산해 넣는다. */
    public void updateEvidence(int evidenceCount, double confidence) {
        this.evidenceCount = evidenceCount;
        this.confidence = BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);
    }
}
