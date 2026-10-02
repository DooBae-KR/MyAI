package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatternEvidenceRepository extends JpaRepository<PatternEvidence, Long> {
    boolean existsByPatternIdAndAnswerId(Long patternId, Long answerId);

    boolean existsByPatternIdAndSubmissionId(Long patternId, Long submissionId);

    int countByPatternId(Long patternId);

    /** 근거의 출처(답변 또는 코딩 풀이와 그 문제 제목)를 화면에 보여 주려고 함께 가져온다. */
    @EntityGraph(attributePaths = {"answer", "submission", "submission.problem"})
    List<PatternEvidence> findTop5ByPatternIdOrderByIdDesc(Long patternId);
}
