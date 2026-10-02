package com.personal.ai.api.learning;

import com.personal.ai.agent.evaluator.DiagnosticQuiz;

public record DiagnosticResponse(Long assessmentId, Long goalId, DiagnosticQuiz quiz) {
}
