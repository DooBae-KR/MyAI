package com.personal.ai.api.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.*;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashboardServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LearningGoalRepository goals = mock(LearningGoalRepository.class);
    private final LearningStepRepository steps = mock(LearningStepRepository.class);
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final DashboardService service = new DashboardService(goals, steps, assessments, mapper);

    private final LearningGoal goal = new LearningGoal(new LearningSubject("Vue", null), "실무", Level.INTERMEDIATE, null);

    private LearningStep step(int seq, StepStatus status) {
        return new LearningStep(goal, seq, "Step" + seq, "목표", 2, status,
                "{\"estimatedMinutes\":" + (seq * 60) + ",\"practiceTasks\":[\"과제" + seq + "\"]}");
    }

    private Assessment gradedDiagnostic() throws Exception {
        Assessment a = new Assessment(goal, null, AssessmentType.DIAGNOSTIC, "{}");
        a.setResult(mapper.writeValueAsString(new GradingResult("1", 35, new Criteria(1, 2, 3, 4, 5, 6),
                List.of(new AreaScore("비동기", 30)), Level.BEGINNER, List.of("개념"), List.of("설명 부족"), List.of())));
        return a;
    }

    @Test
    void listsGoalsWithProgressFromStatusCounts() {
        when(goals.findAllByOrderByIdDesc()).thenReturn(List.of(goal));
        // goal.getId()는 아직 null이므로 null 키로 집계 행을 만든다
        when(steps.countByGoalAndStatus()).thenReturn(List.of(
                new Object[]{null, StepStatus.COMPLETED, 1L},
                new Object[]{null, StepStatus.AVAILABLE, 1L},
                new Object[]{null, StepStatus.LOCKED, 1L}));

        GoalSummary summary = service.goals().get(0);

        assertEquals(3, summary.totalSteps());
        assertEquals(1, summary.completedSteps());
        assertEquals(33, summary.progressPercent());
        assertEquals("Vue", summary.subject());
    }

    @Test
    void goalWithoutStepsHasZeroProgress() {
        when(goals.findAllByOrderByIdDesc()).thenReturn(List.of(goal));
        when(steps.countByGoalAndStatus()).thenReturn(List.of());

        assertEquals(0, service.goals().get(0).progressPercent());
    }

    @Test
    void nextActionFollowsTheLearningFlow() throws Exception {
        when(goals.findById(1L)).thenReturn(Optional.of(goal));
        when(steps.findByGoalIdOrderBySeq(1L)).thenReturn(List.of());
        when(assessments.findFirstByGoalIdAndTypeOrderByIdDesc(1L, AssessmentType.DIAGNOSTIC)).thenReturn(Optional.empty());
        when(assessments.findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(1L, AssessmentType.DIAGNOSTIC))
                .thenReturn(Optional.empty());
        assertEquals("DIAGNOSTIC_NEEDED", service.goal(1L).nextAction());

        Assessment ungraded = new Assessment(goal, null, AssessmentType.DIAGNOSTIC, "{}");
        when(assessments.findFirstByGoalIdAndTypeOrderByIdDesc(1L, AssessmentType.DIAGNOSTIC)).thenReturn(Optional.of(ungraded));
        GoalDetail answersNeeded = service.goal(1L);
        assertEquals("ANSWERS_NEEDED", answersNeeded.nextAction());
        assertNull(answersNeeded.diagnostic());

        Assessment graded = gradedDiagnostic();
        when(assessments.findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(1L, AssessmentType.DIAGNOSTIC))
                .thenReturn(Optional.of(graded));
        GoalDetail curriculumNeeded = service.goal(1L);
        assertEquals("CURRICULUM_NEEDED", curriculumNeeded.nextAction());
        assertEquals(Level.BEGINNER, curriculumNeeded.diagnostic().level());
        assertEquals("설명 부족", curriculumNeeded.diagnostic().weaknesses().get(0));
    }

    @Test
    void detailShowsStepsWithParsedDetailWhenLearning() {
        when(goals.findById(1L)).thenReturn(Optional.of(goal));
        when(steps.findByGoalIdOrderBySeq(1L)).thenReturn(List.of(step(1, StepStatus.AVAILABLE), step(2, StepStatus.LOCKED)));
        when(assessments.findFirstByGoalIdAndTypeOrderByIdDesc(any(), any())).thenReturn(Optional.empty());
        when(assessments.findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(any(), any())).thenReturn(Optional.empty());

        GoalDetail detail = service.goal(1L);

        assertEquals("LEARNING", detail.nextAction());
        assertEquals(2, detail.goal().totalSteps());
        assertEquals(60, detail.steps().get(0).estimatedMinutes());
        assertEquals(List.of("과제2"), detail.steps().get(1).practiceTasks());
        assertEquals(StepStatus.LOCKED, detail.steps().get(1).status());
    }

    @Test
    void missingGoalIsNotFound() {
        when(goals.findById(404L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.goal(404L)).getStatusCode());
    }
}
