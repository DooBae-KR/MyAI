package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LearningAnswerRepository extends JpaRepository<LearningAnswer, Long> {

    /** 아직 패턴 분석을 하지 않은 답변(오래된 순). 질문 내용을 읽으려고 진단까지 함께 가져온다. */
    @EntityGraph(attributePaths = "assessment")
    List<LearningAnswer> findTop30ByAnalyzedAtIsNullOrderByIdAsc();

    long countByAnalyzedAtIsNotNull();

    /** 통계용: 기간 내 답변 시각만 읽는다(날짜별 집계는 서버 시간대로 코드에서 한다). */
    @Query("select a.createdAt from LearningAnswer a where a.createdAt >= :since")
    List<LocalDateTime> createdSince(@Param("since") LocalDateTime since);
}
