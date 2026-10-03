package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatternEvidenceRepository extends JpaRepository<PatternEvidence, Long> {
    boolean existsByPatternIdAndAnswerId(Long patternId, Long answerId);

    boolean existsByPatternIdAndSubmissionId(Long patternId, Long submissionId);

    int countByPatternId(Long patternId);

    /** 신뢰도 계산용: 개선 신호는 빼고 관찰 근거만 센다. */
    int countByPatternIdAndKind(Long patternId, com.personal.ai.core.learning.EvidenceKind kind);


    /** 근거의 출처(답변 또는 코딩 풀이와 그 문제 제목)를 화면에 보여 주려고 함께 가져온다. */
    @EntityGraph(attributePaths = {"answer", "submission", "submission.problem"})
    List<PatternEvidence> findTop5ByPatternIdOrderByIdDesc(Long patternId);
}
