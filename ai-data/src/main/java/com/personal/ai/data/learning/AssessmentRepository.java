package com.personal.ai.data.learning;

import com.personal.ai.core.learning.AssessmentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    /** 목표와 분야까지 한 번에 읽는다. 트랜잭션 밖(LLM 호출 중)에서 지연 로딩하지 않기 위함. */
    @EntityGraph(attributePaths = {"goal", "goal.subject", "step"})
    Optional<Assessment> findWithGoalById(Long id);

    /** 통계용: 채점 완료된 해당 유형의 평가 전부(오래된 순). */
    @EntityGraph(attributePaths = {"goal", "goal.subject"})
    List<Assessment> findByTypeAndResultIsNotNullOrderByIdAsc(AssessmentType type);

    /** 오늘의 학습용: 해당 시각 이후 만들어진 채점 완료 평가(Step, 목표 포함). */
    @EntityGraph(attributePaths = {"step", "goal", "goal.subject"})
    List<Assessment> findByTypeAndCreatedAtGreaterThanEqualAndResultIsNotNullOrderByIdAsc(AssessmentType type, java.time.LocalDateTime since);

    /** Step의 가장 최근 평가(채점 여부 무관). 풀다 만 문제를 이어서 풀게 하려고 쓴다. */
    Optional<Assessment> findFirstByStepIdAndTypeOrderByIdDesc(Long stepId, AssessmentType type);

    /** 목표의 가장 최근 진단(채점 여부 무관). */
    Optional<Assessment> findFirstByGoalIdAndTypeOrderByIdDesc(Long goalId, AssessmentType type);

    /** 목표의 가장 최근 채점 완료 진단. */
    Optional<Assessment> findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(Long goalId, AssessmentType type);
}
