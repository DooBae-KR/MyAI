package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {
    List<LearningStep> findByGoalIdOrderBySeq(Long goalId);

    boolean existsByGoalId(Long goalId);
}
