package com.personal.ai.agent.pattern;

import java.util.List;

public record PatternAnalysis(String promptVersion, List<PatternObservation> observations, List<PatternImprovement> improvements) {
    public PatternAnalysis(String promptVersion, List<PatternObservation> observations) {
        this(promptVersion, observations, List.of());
    }
}
