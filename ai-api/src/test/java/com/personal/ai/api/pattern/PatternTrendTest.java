package com.personal.ai.api.pattern;

import static com.personal.ai.core.learning.EvidenceKind.IMPROVED;
import static com.personal.ai.core.learning.EvidenceKind.OBSERVED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PatternTrendTest {

    @Test
    void looksOnlyAtTheThreeLatestEvidenceItems() {
        assertEquals("NONE", PatternTrend.of(List.of()));
        assertEquals("NONE", PatternTrend.of(List.of(OBSERVED, OBSERVED, OBSERVED, IMPROVED, IMPROVED))); // 개선 신호가 최근 3개 밖이면 무시
        assertEquals("MIXED", PatternTrend.of(List.of(IMPROVED, OBSERVED, OBSERVED)));
        assertEquals("IMPROVING", PatternTrend.of(List.of(IMPROVED, IMPROVED, OBSERVED, OBSERVED)));
        assertEquals("IMPROVING", PatternTrend.of(List.of(IMPROVED, OBSERVED, IMPROVED)));
        assertEquals("MIXED", PatternTrend.of(List.of(OBSERVED, IMPROVED))); // 근거가 적어도 개선 1개는 엇갈림
    }
}
