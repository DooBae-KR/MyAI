package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningGoalRepository extends JpaRepository<LearningGoal, Long> {

    /** 분야와 선행 분야까지 한 번에 읽는다. 트랜잭션 밖(LLM 호출 중)에서 지연 로딩하지 않기 위함. */
    @EntityGraph(attributePaths = {"subject", "subject.prerequisites"})
    Optional<LearningGoal> findWithSubjectById(Long id);
}
