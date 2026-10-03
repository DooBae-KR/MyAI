package com.personal.ai.api.stats;

import java.util.List;

public record StatsResponse(Totals totals, List<Day> activity, List<Count> codingByCategory, List<Count> patternsByStatus) {

    public record Totals(long goals, long stepsTotal, long stepsCompleted, long answers, long submissions, long problems) {}

    /** 최근 N일의 하루 활동. 활동이 없는 날도 0으로 포함한다. */
    public record Day(String date, int answers, int submissions) {}

    public record Count(String name, long count) {}
}
