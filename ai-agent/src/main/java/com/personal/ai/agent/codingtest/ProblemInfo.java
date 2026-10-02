package com.personal.ai.agent.codingtest;

/** 에이전트에 전달하는 문제 정보. 원문이 아니라 메타데이터와 사용자가 직접 쓴 요약이다. */
public record ProblemInfo(String title, String source, String sourceUrl, String company, String difficulty,
                          String category, String algorithm, String dataStructure, String language,
                          String summary, Integer estimatedTime) {
}
