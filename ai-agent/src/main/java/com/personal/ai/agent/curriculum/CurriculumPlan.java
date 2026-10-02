package com.personal.ai.agent.curriculum;

import java.util.List;

public record CurriculumPlan(String promptVersion, List<PlannedStep> steps) {
}
