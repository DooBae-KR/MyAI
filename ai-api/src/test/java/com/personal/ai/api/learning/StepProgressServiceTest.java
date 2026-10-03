package com.personal.ai.api.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void startRejectsLockedAndCompletedSteps() {
        step(1, 1, StepStatus.LOCKED);
        step(2, 2, StepStatus.COMPLETED);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.start(1L)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.start(2L)).getStatusCode());
    }

    @Test
    void completeFinishesStepAndUnlocksOnlyTheNextOne() {
        var s1 = step(1, 1, StepStatus.LEARNING);
        var s2 = new LearningStep(goal, 2, "S2", "o", 2, StepStatus.LOCKED, "{}");
        var s3 = new LearningStep(goal, 3, "S3", "o", 2, StepStatus.LOCKED, "{}");
        when(steps.findByGoalIdOrderBySeq(null)).thenReturn(List.of(s1, s2, s3));

        service.complete(1L);

        assertEquals(StepStatus.COMPLETED, s1.getStatus());
        assertEquals(StepStatus.AVAILABLE, s2.getStatus());
        assertEquals(StepStatus.LOCKED, s3.getStatus());
    }

    @Test
    void completeRequiresLearningAndLastStepHasNothingToUnlock() {
        step(1, 1, StepStatus.AVAILABLE);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.complete(1L)).getStatusCode());

        var last = step(2, 3, StepStatus.LEARNING);
        when(steps.findByGoalIdOrderBySeq(null)).thenReturn(List.of(last));
        service.complete(2L);
        assertEquals(StepStatus.COMPLETED, last.getStatus());
    }

    @Test
    void unknownStepIs404() {
        when(steps.findById(9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.start(9L)).getStatusCode());
    }
}
