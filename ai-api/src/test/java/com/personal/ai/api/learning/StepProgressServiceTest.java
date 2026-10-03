package com.personal.ai.api.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import com.personal.ai.data.learning.LearningSubject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class StepProgressServiceTest {

    private final LearningStepRepository steps = mock(LearningStepRepository.class);
    private final StepProgressService service = new StepProgressService(steps, mock(DashboardService.class));
    private final LearningGoal goal = new LearningGoal(new LearningSubject("Vue", null), "실무", Level.INTERMEDIATE, null);

    private LearningStep step(long id, int seq, StepStatus status) {
        LearningStep s = new LearningStep(goal, seq, "S" + seq, "o", 2, status, "{}");
        ReflectionTestUtils.setField(s, "id", id);
        when(steps.findById(id)).thenReturn(Optional.of(s));
        return s;
    }

    private static HttpStatus statusOf(Runnable r) {
        return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode();
    }

    @Test
    void startMovesAvailableOrReviewStepToLearning() {
        var a = step(1, 1, StepStatus.AVAILABLE);
        var r = step(2, 2, StepStatus.REVIEW_REQUIRED);
        service.start(1L);
        service.start(2L);
        assertEquals(StepStatus.LEARNING, a.getStatus());
        assertEquals(StepStatus.LEARNING, r.getStatus());
    }

    @Test
    void startRejectsLockedLearningAndCompletedSteps() {
        step(1, 1, StepStatus.LOCKED);
        step(2, 2, StepStatus.LEARNING);
        step(3, 3, StepStatus.COMPLETED);
        for (long id = 1; id <= 3; id++) {
            long stepId = id;
            assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.start(stepId)));
        }
    }

    @Test
    void unknownStepIs404() {
        when(steps.findById(9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.start(9L)));
    }

    @Test
    void onlyLearningOrAssessingStepsCanBeAssessed() {
        var learning = step(1, 1, StepStatus.LEARNING);
        service.markAssessing(learning);
        assertEquals(StepStatus.ASSESSMENT, learning.getStatus());
        service.markAssessing(learning); // 이어 풀기

        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.checkAssessable(step(2, 2, StepStatus.AVAILABLE))));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.checkAssessable(step(3, 3, StepStatus.COMPLETED))));
    }

    @Test
    void passingCompletesTheStepAndUnlocksOnlyTheNextOne() {
        var s1 = step(1, 1, StepStatus.ASSESSMENT);
        var s2 = new LearningStep(goal, 2, "S2", "o", 2, StepStatus.LOCKED, "{}");
        var s3 = new LearningStep(goal, 3, "S3", "o", 2, StepStatus.LOCKED, "{}");
        when(steps.findByGoalIdOrderBySeq(null)).thenReturn(List.of(s1, s2, s3));

        StepOutcome outcome = service.applyAssessment(s1, StepProgressService.PASS_SCORE);

        assertTrue(outcome.passed());
        assertEquals(StepStatus.COMPLETED, outcome.stepStatus());
        assertEquals(StepStatus.AVAILABLE, s2.getStatus());
        assertEquals(StepStatus.LOCKED, s3.getStatus());
    }

    @Test
    void failingRequiresReviewAndUnlocksNothing() {
        var s1 = step(1, 1, StepStatus.ASSESSMENT);
        var s2 = new LearningStep(goal, 2, "S2", "o", 2, StepStatus.LOCKED, "{}");
        when(steps.findByGoalIdOrderBySeq(null)).thenReturn(List.of(s1, s2));

        StepOutcome outcome = service.applyAssessment(s1, StepProgressService.PASS_SCORE - 1);

        assertFalse(outcome.passed());
        assertEquals(StepStatus.REVIEW_REQUIRED, s1.getStatus());
        assertEquals(StepStatus.LOCKED, s2.getStatus());
    }

    @Test
    void passingTheLastStepHasNothingToUnlock() {
        var last = step(1, 3, StepStatus.ASSESSMENT);
        when(steps.findByGoalIdOrderBySeq(null)).thenReturn(List.of(last));
        assertEquals(StepStatus.COMPLETED, service.applyAssessment(last, 100).stepStatus());
    }
}
