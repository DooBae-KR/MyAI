package com.personal.ai.data.learning;

import com.personal.ai.core.learning.StepStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "LEARNING_STEP")
public class LearningStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "GOAL_ID")
    private LearningGoal goal;

    @Column(name = "SEQ", nullable = false)
    private int seq;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String objective;

    private Integer difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StepStatus status;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LearningStep() {}

    /** 첫 Step만 AVAILABLE, 나머지는 LOCKED로 시작하는 것은 서비스의 책임이다. */
    public LearningStep(LearningGoal goal, int seq, String title, String objective,
                        Integer difficulty, StepStatus status) {
        this.goal = goal;
        this.seq = seq;
        this.title = title;
        this.objective = objective;
        this.difficulty = difficulty;
        this.status = status;
    }

    public Long getId() { return id; }
    public LearningGoal getGoal() { return goal; }
    public int getSeq() { return seq; }
    public String getTitle() { return title; }
    public String getObjective() { return objective; }
    public Integer getDifficulty() { return difficulty; }
    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
