package com.personal.ai.api.learning;

import com.personal.ai.agent.evaluator.GradingResult;

/** stepOutcome은 Step 확인 문제일 때만 있다(진단이면 null). */
public record GradingResponse(Long assessmentId, GradingResult result, StepOutcome stepOutcome) {
}
