package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LearningStepRepository extends JpaRepository<LearningStep, Long> {
    /** 평가 문제를 만들 때 목표와 분야까지 한 번에 읽는다(LLM 호출 중 지연 로딩 방지). */
    @EntityGraph(attributePaths = {"goal", "goal.subject"})
    java.util.Optional<LearningStep> findWithGoalById(Long id);

    /** 통계/알림용: 해당 상태의 Step 전부(목표·분야 포함). */
    @EntityGraph(attributePaths = {"goal", "goal.subject"})
    List<LearningStep> findByStatusOrderByGoalIdAscSeqAsc(com.personal.ai.core.learning.StepStatus status);

    List<LearningStep> findByGoalIdOrderBySeq(Long goalId);

    boolean existsByGoalId(Long goalId);

    /** 행 형식: [goalId, status, count]. 목표 목록의 진행률 계산용. */
    @Query("select s.goal.id, s.status, count(s) from LearningStep s group by s.goal.id, s.status")
    List<Object[]> countByGoalAndStatus();
}
