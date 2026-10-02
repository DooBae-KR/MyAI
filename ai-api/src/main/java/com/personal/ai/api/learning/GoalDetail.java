package com.personal.ai.api.learning;

import com.personal.ai.agent.evaluator.AreaScore;
import com.personal.ai.core.learning.Level;

import java.util.List;

/** nextAction: DIAGNOSTIC_NEEDED → ANSWERS_NEEDED → CURRICULUM_NEEDED → LEARNING */
public record GoalDetail(GoalSummary goal, String nextAction, Long pendingAssessmentId,
                         Diagnostic diagnostic, List<CurriculumResponse.Step> steps) {

    public record Diagnostic(Long assessmentId, int correctness, Level level, List<AreaScore> areaScores,
                             List<String> strengths, List<String> weaknesses) {}
}
