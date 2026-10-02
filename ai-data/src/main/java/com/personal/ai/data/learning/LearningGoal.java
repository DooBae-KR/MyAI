package com.personal.ai.data.learning;

import com.personal.ai.core.learning.Level;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "LEARNING_GOAL")
public class LearningGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "SUBJECT_ID")
    private LearningSubject subject;

    @Column(nullable = false, length = 1000)
    private String goalText;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Level targetLevel;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Level currentLevel;

    private LocalDate deadline;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LearningGoal() {}

    public LearningGoal(LearningSubject subject, String goalText, Level targetLevel, LocalDate deadline) {
        this.subject = subject;
        this.goalText = goalText;
        this.targetLevel = targetLevel;
        this.deadline = deadline;
    }

    public Long getId() { return id; }
    public LearningSubject getSubject() { return subject; }
    public String getGoalText() { return goalText; }
    public Level getTargetLevel() { return targetLevel; }
    public Level getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(Level currentLevel) { this.currentLevel = currentLevel; }
    public LocalDate getDeadline() { return deadline; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
