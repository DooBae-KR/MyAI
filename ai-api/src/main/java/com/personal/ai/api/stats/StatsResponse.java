package com.personal.ai.api.stats;

import java.util.List;

public record StatsResponse(Totals totals, List<Day> activity, List<Count> codingByCategory, List<Count> patternsByStatus,
                            List<Trend> diagnosticTrends, List<WeakArea> weakAreas, List<Review> reviews) {

    public record Totals(long goals, long stepsTotal, long stepsCompleted, long answers, long submissions, long problems, long studyMinutes) {}

    /** 최근 N일의 하루 활동. 활동이 없는 날도 0으로 포함한다. */
    public record Day(String date, int answers, int submissions, int studyMinutes) {}

    /** 목표별 진단 정답률(0~100) 추이. 진단을 다시 받을 때마다 점이 하나 늘어난다. */
    public record Trend(Long goalId, String subject, List<Point> points) {}

    public record Point(String date, int correctness) {}

    /** 목표별 가장 최근 진단에서 점수가 낮은 영역. */
    public record WeakArea(String subject, String area, int score) {}

    /** 확인 문제에서 합격선에 못 미쳐 다시 학습해야 하는 Step. lastScore/daysAgo는 마지막 확인 문제 기준(없으면 null). */
    public record Review(Long goalId, String subject, Long stepId, int seq, String title, Integer lastScore, Integer daysAgo) {}

    public record Count(String name, long count) {}
}
