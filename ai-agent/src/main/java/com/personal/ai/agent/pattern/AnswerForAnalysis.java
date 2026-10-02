package com.personal.ai.agent.pattern;

/** 분석 대상 답변 한 개와 그 맥락(문제, 채점 결과). */
public record AnswerForAnalysis(long answerId, String area, String type, int difficulty, String question,
                                String answer, Integer score, String feedback) {
}
