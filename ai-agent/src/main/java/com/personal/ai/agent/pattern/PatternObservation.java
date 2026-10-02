package com.personal.ai.agent.pattern;

import java.util.List;

public record PatternObservation(String name, String description, String improvementStrategy,
                                 List<PatternEvidenceItem> evidence) {
}
