package com.personal.ai.api.pattern;

import java.time.LocalDateTime;
import java.util.List;

public final class PatternDtos {

    private PatternDtos() {}

    public record Evidence(Long answerId, String quote, String note) {}

    public record PatternView(Long id, String name, String description, String status, double confidence,
                              int evidenceCount, LocalDateTime firstObservedAt, LocalDateTime lastObservedAt,
                              String improvementStrategy, List<Evidence> evidence) {}

    public record AnalysisResponse(int analyzedAnswers, int newPatterns, int updatedPatterns, String message) {}

    public record UpdateRequest(String status) {}
}
