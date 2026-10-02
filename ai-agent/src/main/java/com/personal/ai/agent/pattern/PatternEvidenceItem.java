package com.personal.ai.agent.pattern;

/** quote는 답변에 실제로 있는 문장이어야 하고, 에이전트가 이를 검증한 것만 돌려준다. */
public record PatternEvidenceItem(long answerId, String quote, String note) {
}
