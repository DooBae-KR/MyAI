package com.personal.ai.api.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.personal.ai.api.learning.CurriculumResponse;
import com.personal.ai.api.learning.DashboardService;
import com.personal.ai.api.learning.GoalDetail;
import com.personal.ai.api.learning.GoalSummary;
import com.personal.ai.core.learning.StepStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarServiceTest {

    private final DashboardService dashboard = mock(DashboardService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T03:00:00Z"), ZoneOffset.UTC); // 서울 12:00
    private final CalendarService service = new CalendarService(dashboard, clock, "Asia/Seoul", "21:00", 14);

    private static CurriculumResponse.Step step(long id, int seq, StepStatus st, int minutes) {
        return new CurriculumResponse.Step(id, seq, "T" + seq, "목표" + seq, 2, minutes, List.of(), st);
    }

    private static GoalDetail goal(long id, String subject, LocalDate deadline, int percent, CurriculumResponse.Step... steps) {
        return new GoalDetail(new GoalSummary(id, subject, "실무, 완성", null, null, deadline, steps.length, 0, percent),
                "LEARNING", null, null, null, List.of(steps));
    }

    private void withGoals(GoalDetail... goals) {
        when(dashboard.goals()).thenReturn(List.of(goals).stream().map(GoalDetail::goal).toList());
        for (GoalDetail g : goals) when(dashboard.goal(g.goal().goalId())).thenReturn(g);
    }

    @Test
    void planPutsReviewStepsFirstSkipsCompletedAndAlternatesBetweenGoals() {
        var vue = goal(1, "Vue", null, 25, step(1, 1, StepStatus.COMPLETED, 60), step(2, 2, StepStatus.AVAILABLE, 60),
                step(3, 3, StepStatus.REVIEW_REQUIRED, 60), step(4, 4, StepStatus.LOCKED, 60));
        var py = goal(2, "Python", null, 0, step(5, 1, StepStatus.AVAILABLE, 60), step(6, 2, StepStatus.LOCKED, 60));

        var titles = CalendarService.plan(List.of(vue, py)).stream().map(p -> p.title()).toList();

        assertEquals(List.of("[Vue] Step 3 T3 복습", "[Python] Step 1 T1 학습", "[Vue] Step 2 T2 학습",
                "[Python] Step 2 T2 학습", "[Vue] Step 4 T4 학습"), titles);
    }

    @Test
    void icsHasDailyEventsAtConfiguredLocalTimeClampedDurationsAndDeadlines() {
        withGoals(goal(1, "Vue", LocalDate.parse("2026-12-31"), 25,
                step(2, 2, StepStatus.AVAILABLE, 10), step(3, 3, StepStatus.LOCKED, 500), step(4, 4, StepStatus.LOCKED, 0)));

        String ics = service.ics().replace("\r\n ", "");

        // 서울 21:00 = 12:00Z, 오늘부터 하루에 하나
        assertTrue(ics.contains("UID:step-2@personal-ai"));
        assertTrue(ics.contains("DTSTART:20261003T120000Z\r\nDTEND:20261003T123000Z"), ics);  // 10분 → 최소 30분
        assertTrue(ics.contains("DTSTART:20261004T120000Z\r\nDTEND:20261004T140000Z"), ics);  // 500분 → 최대 120분
        assertTrue(ics.contains("DTSTART:20261005T120000Z\r\nDTEND:20261005T130000Z"), ics);  // 0분 → 기본 60분
        assertTrue(ics.contains("UID:deadline-1@personal-ai"));
        assertTrue(ics.contains("SUMMARY:마감: Vue"));
        assertTrue(ics.contains("DESCRIPTION:실무\\, 완성 (진행률 25%)")); // 쉼표 이스케이프
    }

    @Test
    void finishedGoalsHaveNoDeadlineEventAndPlanIsCappedByDays() {
        List<CurriculumResponse.Step> many = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) many.add(step(i, i, StepStatus.LOCKED, 60));
        withGoals(goal(1, "Done", LocalDate.parse("2026-12-31"), 100), goal(2, "Long", null, 0, many.toArray(new CurriculumResponse.Step[0])));

        String ics = service.ics();

        assertFalse(ics.contains("deadline-1"));
        assertEquals(14, ics.split("BEGIN:VEVENT", -1).length - 1); // 14일치만
    }
}
