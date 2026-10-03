package com.personal.ai.api.today;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.Criteria;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.api.learning.CurriculumResponse;
import com.personal.ai.api.learning.DashboardService;
import com.personal.ai.api.learning.GoalDetail;
import com.personal.ai.api.learning.GoalSummary;
import com.personal.ai.api.stats.StatsResponse;
import com.personal.ai.api.stats.StatsService;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.codingtest.CodingLecture;
import com.personal.ai.data.codingtest.CodingLectureRepository;
import com.personal.ai.data.codingtest.CodingProblem;
import com.personal.ai.data.codingtest.CodingProblemRepository;
import com.personal.ai.data.codingtest.CodingSubmissionRepository;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningSubject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TodayServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DashboardService dashboard = mock(DashboardService.class);
    private final StatsService stats = mock(StatsService.class);
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final CodingProblemRepository problems = mock(CodingProblemRepository.class);
    private final CodingLectureRepository lectures = mock(CodingLectureRepository.class);
    private final CodingSubmissionRepository submissions = mock(CodingSubmissionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
    private final TodayService service = new TodayService(dashboard, stats, assessments, problems, lectures, submissions, mapper, clock);

    private static GoalDetail goal(String next, CurriculumResponse.Step... steps) {
        return new GoalDetail(new GoalSummary(1L, "Vue", "실무", null, null, null, 4, 1, 25), next, null, null, null, List.of(steps));
    }

    private static CurriculumResponse.Step step(int seq, StepStatus st) {
        return new CurriculumResponse.Step((long) seq, seq, "T" + seq, "o", 2, 30, List.of(), st);
    }

    private void withGoals(GoalDetail... details) {
        when(dashboard.goals()).thenReturn(List.of(details).stream().map(d -> d.goal()).toList());
        for (GoalDetail d : details) when(dashboard.goal(d.goal().goalId())).thenReturn(d);
    }

    @Test
    void nextTodoPerGoalSkipsCompletedLockedAndReviewSteps() {
        assertEquals("Vue · Step 3 T3 학습 시작", TodayService.next(goal("LEARNING", step(1, StepStatus.COMPLETED),
                step(2, StepStatus.REVIEW_REQUIRED), step(3, StepStatus.AVAILABLE), step(4, StepStatus.LOCKED))).get().label());
        assertEquals("Vue · Step 2 T2 확인 문제 풀기", TodayService.next(goal("LEARNING", step(1, StepStatus.COMPLETED), step(2, StepStatus.ASSESSMENT))).get().label());
        assertEquals("Vue · Step 1 T1 학습 이어서", TodayService.next(goal("LEARNING", step(1, StepStatus.LEARNING))).get().label());
        assertTrue(TodayService.next(goal("LEARNING", step(1, StepStatus.COMPLETED))).isEmpty());
        assertEquals("DIAGNOSTIC", TodayService.next(goal("DIAGNOSTIC_NEEDED")).get().kind());
        assertEquals("ANSWERS", TodayService.next(goal("ANSWERS_NEEDED")).get().kind());
        assertEquals("CURRICULUM", TodayService.next(goal("CURRICULUM_NEEDED")).get().kind());
    }

    @Test
    void todosListTodaysPassedStepsFirstThenNextStepsThenReviews() throws Exception {
        withGoals(goal("LEARNING", step(1, StepStatus.COMPLETED), step(2, StepStatus.AVAILABLE)));
        LearningGoal g = mock(LearningGoal.class);
        when(g.getId()).thenReturn(1L);
        when(g.getSubject()).thenReturn(new LearningSubject("Vue", null));
        LearningStep s1 = new LearningStep(g, 1, "T1", "o", 2, StepStatus.COMPLETED, "{}");
        Assessment passed = new Assessment(g, s1, AssessmentType.STEP, "{}");
        passed.setResult(mapper.writeValueAsString(new GradingResult("1", 92, new Criteria(1, 2, 3, 4, 5, 6), List.of(), Level.BEGINNER, List.of(), List.of(), List.of())));
        Assessment failed = new Assessment(g, s1, AssessmentType.STEP, "{}");
        failed.setResult(mapper.writeValueAsString(new GradingResult("1", 50, new Criteria(1, 2, 3, 4, 5, 6), List.of(), Level.BEGINNER, List.of(), List.of(), List.of())));
        when(assessments.findByTypeAndCreatedAtGreaterThanEqualAndResultIsNotNullOrderByIdAsc(any(), any()))
                .thenReturn(List.of(passed, failed));
        when(stats.reviews()).thenReturn(List.of(new StatsResponse.Review(1L, "Vue", 9L, 4, "R4", 55, 1)));

        var todos = service.today().todos();

        assertEquals(List.of("PASSED", "STEP", "REVIEW"), todos.stream().map(TodayResponse.Todo::kind).toList());
        assertTrue(todos.get(0).done());
        assertEquals("Vue · Step 1 T1 합격 (92점)", todos.get(0).label());
        assertEquals("Vue · Step 4 R4 복습 (마지막 55점)", todos.get(2).label());
        assertEquals("#/goals/1", todos.get(1).link());
    }

    private CodingProblem problem(long id, String title) {
        CodingProblem p = mock(CodingProblem.class);
        when(p.getId()).thenReturn(id);
        when(p.getTitle()).thenReturn(title);
        return p;
    }

    @Test
    void lectureIsTheOldestProblemWithLectureAndNoSubmissionElseLatestLecture() {
        when(dashboard.goals()).thenReturn(List.of());
        var p1 = problem(1, "BFS 최단거리");
        var p2 = problem(2, "해시");
        var p3 = problem(3, "강의 없음");
        when(problems.findAllByOrderByIdDesc()).thenReturn(List.of(p3, p2, p1));
        when(lectures.findFirstByProblemIdOrderByIdDesc(1L)).thenReturn(Optional.of(mock(CodingLecture.class)));
        when(lectures.findFirstByProblemIdOrderByIdDesc(2L)).thenReturn(Optional.of(mock(CodingLecture.class)));
        when(lectures.findFirstByProblemIdOrderByIdDesc(3L)).thenReturn(Optional.empty());
        when(submissions.countByProblemId(1L)).thenReturn(2);
        when(submissions.countByProblemId(2L)).thenReturn(0);

        assertEquals("해시", service.today().lecture().title()); // 풀이 없는 특강 우선

        when(submissions.countByProblemId(2L)).thenReturn(1);
        assertEquals("해시", service.today().lecture().title()); // 모두 풀었으면 가장 최근 특강
        assertEquals(2L, service.today().lecture().problemId());

        when(problems.findAllByOrderByIdDesc()).thenReturn(List.of(p3));
        assertNull(service.today().lecture());
    }
}
