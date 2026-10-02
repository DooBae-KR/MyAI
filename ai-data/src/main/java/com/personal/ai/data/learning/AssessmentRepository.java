package com.personal.ai.data.learning;

import com.personal.ai.core.learning.AssessmentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    /** 목표와 분야까지 한 번에 읽는다. 트랜잭션 밖(LLM 호출 중)에서 지연 로딩하지 않기 위함. */
    @EntityGraph(attributePaths = {"goal", "goal.subject"})
    Optional<Assessment> findWithGoalById(Long id);

    /** 목표의 가장 최근 진단(채점 여부 무관). */
    Optional<Assessment> findFirstByGoalIdAndTypeOrderByIdDesc(Long goalId, AssessmentType type);

    /** 목표의 가장 최근 채점 완료 진단. */
    Optional<Assessment> findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(Long goalId, AssessmentType type);
}
