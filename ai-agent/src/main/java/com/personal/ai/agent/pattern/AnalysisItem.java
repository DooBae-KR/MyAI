package com.personal.ai.agent.pattern;

/**
 * 분석 대상 한 개와 그 맥락. itemId는 "A12"(진단 답변), "S3"(코딩 풀이)처럼 출처를 포함한다.
 * kind가 ANSWER면 answer는 학습자의 답변, CODE면 학습자의 접근 설명과 코드이다.
 * feedback은 채점 의견 또는 AI의 풀이 리뷰이며 학습자가 쓴 글이 아니다(근거 인용에 쓸 수 없다).
 */
public record AnalysisItem(String itemId, String kind, String area, String type, int difficulty, String question,
                           String answer, Integer score, String feedback) {
}
