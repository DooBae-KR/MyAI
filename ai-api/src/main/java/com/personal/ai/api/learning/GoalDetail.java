package com.personal.ai.api.learning;

import com.personal.ai.agent.evaluator.AreaScore;
import com.personal.ai.core.learning.Level;

import java.util.List;

/** nextAction: DIAGNOSTIC_NEEDED → ANSWERS_NEEDED → CURRICULUM_NEEDED → LEARNING. ANSWERS_NEEDED일 때만 pendingQuiz가 있다. */
public record GoalDetail(GoalSummary goal, String nextAction, Long pendingAssessmentId, QuizView pendingQuiz,
                         Diagnostic diagnostic, List<CurriculumResponse.Step> steps) {

    public record Diagnostic(Long assessmentId, int correctness, Level level, List<AreaScore> areaScores,
                             List<String> strengths, List<String> weaknesses) {}
}
