package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatternEvidenceRepository extends JpaRepository<PatternEvidence, Long> {
    boolean existsByPatternIdAndAnswerId(Long patternId, Long answerId);

    int countByPatternId(Long patternId);

    List<PatternEvidence> findTop5ByPatternIdOrderByIdDesc(Long patternId);
}
