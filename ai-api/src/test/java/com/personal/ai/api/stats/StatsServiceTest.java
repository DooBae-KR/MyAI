package com.personal.ai.api.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.personal.ai.core.learning.PatternStatus;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.codingtest.CodingProblemRepository;
import com.personal.ai.data.codingtest.CodingSubmissionRepository;
import com.personal.ai.data.learning.LearningAnswerRepository;
import com.personal.ai.data.learning.LearningGoalRepository;
import com.personal.ai.data.learning.LearningStepRepository;
import com.personal.ai.data.learning.ThinkingPatternRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

    private final LearningGoalRepository goals = mock(LearningGoalRepository.class);
    private final LearningStepRepository steps = mock(LearningStepRepository.class);
    private final LearningAnswerRepository answers = mock(LearningAnswerRepository.class);
    private final CodingSubmissionRepository submissions = mock(CodingSubmissionRepository.class);
    private final CodingProblemRepository problems = mock(CodingProblemRepository.class);
    private final ThinkingPatternRepository patterns = mock(ThinkingPatternRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
    private final StatsService service = new StatsService(goals, steps, answers, submissions, problems, patterns, clock);

    @Test
    void aggregatesTotalsActivityAndBreakdowns() {
        when(goals.count()).thenReturn(2L);
        when(answers.count()).thenReturn(3L);
        when(submissions.count()).thenReturn(4L);
        when(problems.count()).thenReturn(5L);
        when(steps.countByGoalAndStatus()).thenReturn(List.of(
                new Object[] {1L, StepStatus.COMPLETED, 2L},
                new Object[] {1L, StepStatus.AVAILABLE, 3L},
                new Object[] {2L, StepStatus.COMPLETED, 1L}));
        when(answers.createdSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                LocalDateTime.parse("2026-10-03T09:00:00"), LocalDateTime.parse("2026-10-03T10:00:00"),
                LocalDateTime.parse("2026-10-01T10:00:00")));
        when(submissions.createdSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                LocalDateTime.parse("2026-10-03T11:00:00")));
        when(submissions.countByCategory()).thenReturn(List.of(
                new Object[] {"그래프", 3L}, new Object[] {null, 1L}));
        when(patterns.countByStatus()).thenReturn(List.<Object[]>of(new Object[] {PatternStatus.SUPPORTED, 2L}));

        StatsResponse r = service.stats();

        assertEquals(new StatsResponse.Totals(2, 6, 3, 3, 4, 5), r.totals());
        assertEquals(StatsService.DAYS, r.activity().size());
        assertEquals(new StatsResponse.Day("2026-10-03", 2, 1), r.activity().get(StatsService.DAYS - 1));
        assertEquals(new StatsResponse.Day("2026-10-02", 0, 0), r.activity().get(StatsService.DAYS - 2));
        assertEquals(new StatsResponse.Day("2026-10-01", 1, 0), r.activity().get(StatsService.DAYS - 3));
        assertEquals("분류 없음", r.codingByCategory().get(1).name());
        assertEquals(new StatsResponse.Count("SUPPORTED", 2), r.patternsByStatus().get(0));
    }
}
