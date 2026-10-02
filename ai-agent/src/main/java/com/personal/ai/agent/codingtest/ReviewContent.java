package com.personal.ai.agent.codingtest;

import java.util.List;

/** 내 풀이와 권장 풀이의 비교. 코드를 실행한 결과가 아니라 읽고 분석한 의견이다. */
public record ReviewContent(String promptVersion, String userApproach, String recommendedApproach,
                            List<Comparison> comparisons, List<String> mistakes, List<String> nextSteps,
                            List<String> similarProblems, String summary) {

    /** status: SAME(같음), DIFFERENT(다름), UNKNOWN(코드만으로 판단 어려움) */
    public record Comparison(String item, String mine, String recommended, String status, String reason) {}
}
