package com.personal.ai.data.learning;

import com.personal.ai.core.learning.PatternStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ThinkingPatternRepository extends JpaRepository<ThinkingPattern, Long> {
    Optional<ThinkingPattern> findByNameIgnoreCase(String name);

    List<ThinkingPattern> findByStatusNot(PatternStatus status);

    /** 행 형식: [상태, 개수]. */
    @Query("select p.status, count(p) from ThinkingPattern p group by p.status")
    List<Object[]> countByStatus();

    /** 기각된 패턴은 맨 아래로. */
    List<ThinkingPattern> findAllByOrderByStatusDescConfidenceDescIdAsc();
}
