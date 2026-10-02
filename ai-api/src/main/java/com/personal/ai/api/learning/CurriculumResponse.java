package com.personal.ai.api.learning;

import com.personal.ai.core.learning.StepStatus;

import java.util.List;

public record CurriculumResponse(Long goalId, String promptVersion, List<Step> steps) {

    public record Step(Long id, int seq, String title, String objective, int difficulty,
                       int estimatedMinutes, List<String> practiceTasks, StepStatus status) {}
}
