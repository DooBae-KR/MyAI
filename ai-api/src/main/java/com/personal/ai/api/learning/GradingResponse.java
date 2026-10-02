package com.personal.ai.api.learning;

import com.personal.ai.agent.evaluator.GradingResult;

public record GradingResponse(Long assessmentId, GradingResult result) {
}
