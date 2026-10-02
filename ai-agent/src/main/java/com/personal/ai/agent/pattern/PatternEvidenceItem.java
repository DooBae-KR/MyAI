package com.personal.ai.agent.pattern;

/** quote는 해당 항목에서 학습자가 쓴 글(답변, 접근 설명, 코드)에 실제로 있는 문장이어야 하고, 에이전트가 이를 검증한 것만 돌려준다. */
public record PatternEvidenceItem(String itemId, String quote, String note) {
}
