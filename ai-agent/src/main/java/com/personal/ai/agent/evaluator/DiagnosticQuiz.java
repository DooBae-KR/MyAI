package com.personal.ai.agent.evaluator;

import java.util.List;

public record DiagnosticQuiz(String promptVersion, List<DiagnosticQuestion> questions) {
}
