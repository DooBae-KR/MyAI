package com.personal.ai.api.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.personal.ai.core.learning.PatternStatus;
import com.personal.ai.core.learning.StepStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.AreaScore;
import com.personal.ai.agent.evaluator.Criteria;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.data.codingtest.CodingProblemRepository;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningSubject;
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
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
    private final StatsService service = new StatsService(goals, steps, answers, submissions, problems, patterns, assessments, mapper, clock);

    private Assessment graded(LearningGoal goal, Long id, int correctness, AreaScore... areas) throws Exception {
        Assessment a = new Assessment(goal, null, AssessmentType.DIAGNOSTIC, "{}");
        org.springframework.test.util.ReflectionTestUtils.setField(a, "id", id);
        org.springframework.test.util.ReflectionTestUtils.setField(a, "createdAt", LocalDateTime.parse("2026-10-0" + id + "T09:00:00"));
        a.setResult(mapper.writeValueAsString(new GradingResult("1", correctness, new Criteria(1, 2, 3, 4, 5, 6),
                List.of(areas), Level.BEGINNER, List.of(), List.of(), List.of())));
        return a;
    }

    @Test
    void reviewsListStepsNeedingReviewLowestScoreFirstWithUnknownLast() throws Exception {
        LearningGoal goal = mock(LearningGoal.class);
        when(goal.getId()).thenReturn(7L);
        when(goal.getSubject()).thenReturn(new LearningSubject("Vue", null));
        var s1 = new com.personal.ai.data.learning.LearningStep(goal, 1, "A", "o", 2, StepStatus.REVIEW_REQUIRED, "{}");
        var s2 = new com.personal.ai.data.learning.LearningStep(goal, 2, "B", "o", 2, StepStatus.REVIEW_REQUIRED, "{}");
        var s3 = new com.personal.ai.data.learning.LearningStep(goal, 3, "C", "o", 2, StepStatus.REVIEW_REQUIRED, "{}");
        org.springframework.test.util.ReflectionTestUtils.setField(s1, "id", 11L);
        org.springframework.test.util.ReflectionTestUtils.setField(s2, "id", 12L);
        org.springframework.test.util.ReflectionTestUtils.setField(s3, "id", 13L);
        when(steps.findByStatusOrderByGoalIdAscSeqAsc(StepStatus.REVIEW_REQUIRED)).thenReturn(List.of(s1, s2, s3));
        Assessment a1 = graded(goal, 1L, 65, new AreaScore("x", 1));
        Assessment a2 = graded(goal, 2L, 40, new AreaScore("x", 1));
        when(assessments.findFirstByStepIdAndTypeOrderByIdDesc(11L, AssessmentType.STEP)).thenReturn(java.util.Optional.of(a1));
        when(assessments.findFirstByStepIdAndTypeOrderByIdDesc(12L, AssessmentType.STEP)).thenReturn(java.util.Optional.of(a2));
        when(assessments.findFirstByStepIdAndTypeOrderByIdDesc(13L, AssessmentType.STEP)).thenReturn(java.util.Optional.empty());

        var r = service.stats().reviews();

        assertEquals(List.of("B", "A", "C"), r.stream().map(StatsResponse.Review::title).toList());
        assertEquals(40, r.get(0).lastScore());
        assertEquals(1, r.get(0).daysAgo()); // 10-02 09:00 → 오늘(10-03)
        assertEquals(null, r.get(2).lastScore());
    }

    @Test
    void trendsFollowEachDiagnosticAndWeakAreasUseTheLatestOne() throws Exception {
        LearningGoal goal = mock(LearningGoal.class);
        when(goal.getId()).thenReturn(7L);
        when(goal.getSubject()).thenReturn(new LearningSubject("Vue", null));
        when(assessments.findByTypeAndResultIsNotNullOrderByIdAsc(AssessmentType.DIAGNOSTIC)).thenReturn(List.of(
                graded(goal, 1L, 35, new AreaScore("비동기", 10), new AreaScore("상태", 90)),
                graded(goal, 2L, 60, new AreaScore("비동기", 40), new AreaScore("상태", 80))));

        StatsResponse r = service.stats();

        assertEquals(List.of(new StatsResponse.Point("2026-10-01", 35), new StatsResponse.Point("2026-10-02", 60)),
                r.diagnosticTrends().get(0).points());
        assertEquals("Vue", r.diagnosticTrends().get(0).subject());
        assertEquals(List.of(new StatsResponse.WeakArea("Vue", "비동기", 40), new StatsResponse.WeakArea("Vue", "상태", 80)),
                r.weakAreas());
    }

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
