package com.personal.ai.agent.evaluator;

import java.util.List;

public record DiagnosticQuestion(int id, String area, String type, int difficulty,
                                 String question, List<String> keyPoints) {
}
