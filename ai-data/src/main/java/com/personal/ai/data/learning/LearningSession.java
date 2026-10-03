package com.personal.ai.data.learning;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Step을 하루 동안 공부한 시간의 합(초). (step, 날짜)당 한 행. */
@Entity
@Table(name = "LEARNING_SESSION")
public class LearningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "STEP_ID")
    private LearningStep step;

    @Column(nullable = false)
    private LocalDate studyDate;

    @Column(nullable = false)
    private int seconds;

    private LocalDateTime updatedAt = LocalDateTime.now();

    protected LearningSession() {}

    public LearningSession(LearningStep step, LocalDate studyDate, int seconds) {
        this.step = step;
        this.studyDate = studyDate;
        this.seconds = seconds;
    }

    public Long getId() { return id; }
    public LearningStep getStep() { return step; }
    public LocalDate getStudyDate() { return studyDate; }
    public int getSeconds() { return seconds; }
    public void setSeconds(int seconds) {
        this.seconds = seconds;
        this.updatedAt = LocalDateTime.now();
    }
}
