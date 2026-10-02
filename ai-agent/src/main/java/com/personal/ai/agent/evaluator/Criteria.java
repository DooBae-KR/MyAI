package com.personal.ai.agent.evaluator;

/** LLM의 정성 평가(0~100). correctness는 점수에서 코드가 계산하므로 여기 없다. */
public record Criteria(int conceptUnderstanding, int reasoningQuality, int problemSolving,
                       int codeQuality, int structure, int communication) {
}
