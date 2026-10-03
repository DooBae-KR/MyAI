package com.personal.ai.api.pattern;

import com.personal.ai.core.learning.EvidenceKind;

import java.util.List;

/**
 * 개선 훈련의 효과를 코드가 판단한다(LLM이 아니라). 가장 최근 근거 3개(최신순) 중 개선 신호가 몇 개인지로 본다.
 * 개선 신호는 한 번의 좋은 답변일 뿐이라 2개 이상이어야 "개선 중"이라 하고, 1개면 "엇갈림"이다.
 */
final class PatternTrend {

    static final int WINDOW = 3;
    static final int IMPROVING_AT = 2;

    private PatternTrend() {}

    /** @param latestFirst 최신 근거부터 나열한 종류 */
    static String of(List<EvidenceKind> latestFirst) {
        long improved = latestFirst.stream().limit(WINDOW).filter(k -> k == EvidenceKind.IMPROVED).count();
        if (improved >= IMPROVING_AT) return "IMPROVING";
        return improved >= 1 ? "MIXED" : "NONE";
    }
}
