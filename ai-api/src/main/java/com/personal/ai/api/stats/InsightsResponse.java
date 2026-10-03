package com.personal.ai.api.stats;

import java.util.List;

/** 설계서 21장의 나머지 통계. 평균 점수·재학습 횟수, 알고리즘별 일치율, 언어·분야별 학습량, 영역 변화, 사고 패턴 변화. */
public record InsightsResponse(Assessments assessments, List<Algorithm> algorithms, List<StatsResponse.Count> languages,
                               List<StatsResponse.Count> studyMinutesBySubject, List<AreaChange> areaChanges,
                               List<PatternChange> patterns) {

    /** Step 확인 문제 채점 결과. averageScore는 채점 기록이 없으면 null. relearnCount는 합격선에 못 미쳐 다시 학습하게 된 횟수. */
    public record Assessments(int attempts, int passed, int relearnCount, Integer averageScore) {}

    /** 코드를 실행해 채점하지 않으므로 "정답률"이 아니라, AI 풀이 비교에서 권장 접근과 같다고 본 항목의 비율이다. */
    public record Algorithm(String name, int submissions, int sameItems, int comparedItems, int matchPercent) {}

    /** 같은 목표의 첫 진단과 가장 최근 진단의 영역 점수 변화. */
    public record AreaChange(String subject, String area, int first, int latest, int delta) {}

    public record PatternChange(String name, String status, int observed, int improved, String trend) {}
}
