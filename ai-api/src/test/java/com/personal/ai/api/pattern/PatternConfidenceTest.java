package com.personal.ai.api.pattern;

import com.personal.ai.core.learning.PatternStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatternConfidenceTest {

    @Test
    void fewObservationsStayTentativeNoMatterHowDominant() {
        assertEquals(0.0, PatternConfidence.confidence(0, 10));
        assertEquals(0.0, PatternConfidence.confidence(3, 0));
        // 근거가 1~2개면 전체 답변의 100%여도 0.4를 넘지 못한다: 한 번의 답변으로 단정하지 않는다
        assertEquals(0.18, PatternConfidence.confidence(1, 1)); // 상한 0.9 × 표본 보정 0.2
        assertEquals(0.36, PatternConfidence.confidence(2, 2)); // 상한 0.9 × 표본 보정 0.4
        assertTrue(PatternConfidence.confidence(2, 2) <= 0.4);
    }

    @Test
    void confidenceGrowsWithSupportAndRatioButNeverExceedsPointNine() {
        assertEquals(0.18, PatternConfidence.confidence(3, 10)); // 비율 0.3 × 표본 보정 0.6
        assertEquals(0.5, PatternConfidence.confidence(5, 10));
        assertEquals(0.9, PatternConfidence.confidence(10, 10));
        assertEquals(0.9, PatternConfidence.confidence(50, 40)); // support가 total보다 커도 상한
        assertTrue(PatternConfidence.confidence(4, 10) < PatternConfidence.confidence(8, 10));
        // 같은 근거 개수라도 분석한 답변이 늘어 비율이 낮아지면 신뢰도가 내려간다
        assertTrue(PatternConfidence.confidence(5, 30) < PatternConfidence.confidence(5, 10));
    }

    @Test
    void supportedNeedsThreeDifferentAnswers() {
        assertEquals(PatternStatus.HYPOTHESIS, PatternConfidence.status(0));
        assertEquals(PatternStatus.HYPOTHESIS, PatternConfidence.status(2));
        assertEquals(PatternStatus.SUPPORTED, PatternConfidence.status(3));
    }
}
