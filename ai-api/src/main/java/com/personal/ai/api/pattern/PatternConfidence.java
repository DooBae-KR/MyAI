package com.personal.ai.api.pattern;

import com.personal.ai.core.learning.PatternStatus;

/**
 * 패턴의 신뢰도와 상태는 LLM이 아니라 근거 개수로 계산한다.
 * support: 이 패턴을 뒷받침하는 서로 다른 답변 수, total: 지금까지 분석한 전체 답변 수.
 */
public final class PatternConfidence {

    /** 이 개수 이상의 서로 다른 답변에서 관찰되어야 "반복 관찰됨"이다. */
    public static final int SUPPORTED_FROM = 3;
    /** 근거가 이만큼 쌓여야 표본 보정 없이 비율을 그대로 쓴다. */
    private static final double FULL_SAMPLE = 5.0;
    private static final double MAX = 0.9;
    private static final double TENTATIVE_CAP = 0.4;

    private PatternConfidence() {}

    /** 비율(support/total)에 표본 크기 보정을 곱한다. 근거가 3개 미만이면 0.4를 넘지 못한다. 어떤 경우에도 0.9 이하. */
    public static double confidence(int support, int total) {
        if (support <= 0 || total <= 0) {
            return 0;
        }
        double ratio = Math.min(MAX, (double) support / total);
        double value = ratio * Math.min(1.0, support / FULL_SAMPLE);
        if (support < SUPPORTED_FROM) {
            value = Math.min(value, TENTATIVE_CAP);
        }
        return Math.round(value * 100) / 100.0;
    }

    /** 기각(DISMISSED)은 사용자의 결정이라 여기서 다루지 않는다. */
    public static PatternStatus status(int support) {
        return support >= SUPPORTED_FROM ? PatternStatus.SUPPORTED : PatternStatus.HYPOTHESIS;
    }
}
