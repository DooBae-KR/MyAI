package com.personal.ai.agent.pattern;

/** 이미 알려진 패턴에 대해, 이번 항목에서 개선 훈련대로(패턴과 반대로) 쓴 개선 신호. 인용은 학습자의 글에서 검증된 것이다. */
public record PatternImprovement(String patternName, PatternEvidenceItem evidence) {
}
