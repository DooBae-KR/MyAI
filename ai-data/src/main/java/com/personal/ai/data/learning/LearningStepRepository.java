package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {
    List<LearningStep> findByGoalIdOrderBySeq(Long goalId);

    boolean existsByGoalId(Long goalId);

    /** 행 형식: [goalId, status, count]. 목표 목록의 진행률 계산용. */
    @Query("select s.goal.id, s.status, count(s) from LearningStep s group by s.goal.id, s.status")
    List<Object[]> countByGoalAndStatus();
}
