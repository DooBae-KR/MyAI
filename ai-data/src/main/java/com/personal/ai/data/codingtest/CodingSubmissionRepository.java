package com.personal.ai.data.codingtest;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodingSubmissionRepository extends JpaRepository<CodingSubmission, Long> {
    List<CodingSubmission> findByProblemIdOrderByIdDesc(Long problemId);

    int countByProblemId(Long problemId);

    /** 아직 패턴 분석을 하지 않은 제출(오래된 순). 코드가 길어 한 번에 10개까지만 읽는다. 문제 정보를 함께 가져온다. */
    @EntityGraph(attributePaths = "problem")
    List<CodingSubmission> findTop10ByAnalyzedAtIsNullOrderByIdAsc();

    long countByAnalyzedAtIsNotNull();
}
