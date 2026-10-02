package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningAnswerRepository extends JpaRepository<LearningAnswer, Long> {

    /** 아직 패턴 분석을 하지 않은 답변(오래된 순). 질문 내용을 읽으려고 진단까지 함께 가져온다. */
    @EntityGraph(attributePaths = "assessment")
    List<LearningAnswer> findTop30ByAnalyzedAtIsNullOrderByIdAsc();

    long countByAnalyzedAtIsNotNull();
}
