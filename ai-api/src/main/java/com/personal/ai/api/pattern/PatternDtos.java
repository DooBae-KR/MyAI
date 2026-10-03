package com.personal.ai.api.pattern;

import java.time.LocalDateTime;
import java.util.List;

public final class PatternDtos {

    private PatternDtos() {}

    /** kind: OBSERVED(관찰 근거) 또는 IMPROVED(개선 신호). trend: NONE, MIXED, IMPROVING(최근 근거 3개 중 개선 신호 2개 이상). */
    /** source: ANSWER(진단 답변) 또는 SUBMISSION(코딩 풀이). label은 화면에 보여 줄 출처 이름. */
    public record Evidence(String source, Long itemId, String label, String quote, String note, String kind) {}

    public record PatternView(Long id, String name, String description, String status, double confidence,
                              int evidenceCount, LocalDateTime firstObservedAt, LocalDateTime lastObservedAt,
                              String improvementStrategy, int improvedCount, String trend, List<Evidence> evidence) {}

    public record AnalysisResponse(int analyzedAnswers, int analyzedSubmissions, int newPatterns, int updatedPatterns, int improvements, String message) {}

    public record UpdateRequest(String status) {}
}
