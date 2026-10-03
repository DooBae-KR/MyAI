package com.personal.ai.api.learning;

import com.personal.ai.core.learning.StepStatus;

/** Step 확인 문제 채점의 결과: 합격 여부와 Step의 새 상태. */
public record StepOutcome(boolean passed, int passScore, StepStatus stepStatus) {
}
