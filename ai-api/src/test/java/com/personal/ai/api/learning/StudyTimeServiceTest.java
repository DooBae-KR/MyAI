package com.personal.ai.api.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningSession;
import com.personal.ai.data.learning.LearningSessionRepository;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import com.personal.ai.data.learning.LearningSubject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class StudyTimeServiceTest {

    private final LearningStepRepository steps = mock(LearningStepRepository.class);
    private final LearningSessionRepository sessions = mock(LearningSessionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
    private final StudyTimeService service = new StudyTimeService(steps, sessions,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), clock);
    private final LearningGoal goal = new LearningGoal(new LearningSubject("Vue", null), "실무", Level.INTERMEDIATE, null);
    private static final LocalDate TODAY = LocalDate.parse("2026-10-03");

    private LearningStep step(StepStatus status) {
        LearningStep s = new LearningStep(goal, 1, "S", "o", 2, status, "{}");
        ReflectionTestUtils.setField(s, "id", 1L);
        when(steps.findById(1L)).thenReturn(Optional.of(s));
        return s;
    }

    private static int status(Runnable r) {
        return ((ResponseStatusException) assertThrows(ResponseStatusException.class, r::run)).getStatusCode().value();
    }

    @Test
    void createsTodaysRowThenAddsToIt() {
        LearningStep s = step(StepStatus.LEARNING);
        LearningSession row = new LearningSession(s, TODAY, 0);
        when(sessions.findByStepIdAndStudyDate(1L, TODAY)).thenReturn(Optional.empty(), Optional.of(row));
        when(sessions.saveAndFlush(any())).thenReturn(row);
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(30, service.add(1L, 30));
        assertEquals(60, service.add(1L, 30));
        verify(sessions, times(1)).saveAndFlush(any()); // 행은 하루에 한 번만 만든다
    }

    @Test
    void dailyTotalPerStepIsCapped() {
        LearningStep s = step(StepStatus.LEARNING);
        LearningSession row = new LearningSession(s, TODAY, StudyTimeService.MAX_SECONDS_PER_STEP_PER_DAY - 10);
        when(sessions.findByStepIdAndStudyDate(1L, TODAY)).thenReturn(Optional.of(row));
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(StudyTimeService.MAX_SECONDS_PER_STEP_PER_DAY, service.add(1L, 120));
    }

    @Test
    void retriesOnceWhenTheFirstRowIsCreatedConcurrently() {
        LearningStep s = step(StepStatus.AVAILABLE);
        LearningSession existing = new LearningSession(s, TODAY, 40);
        when(sessions.findByStepIdAndStudyDate(1L, TODAY)).thenReturn(Optional.empty(), Optional.of(existing));
        when(sessions.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_learning_session_step_date"));
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(70, service.add(1L, 30));
    }

    @Test
    void rejectsBadSecondsLockedAndUnknownSteps() {
        step(StepStatus.LOCKED);
        assertEquals(400, status(() -> service.add(1L, 0)));
        assertEquals(400, status(() -> service.add(1L, StudyTimeService.MAX_SECONDS_PER_CALL + 1)));
        assertEquals(409, status(() -> service.add(1L, 30)));
        when(steps.findById(9L)).thenReturn(Optional.empty());
        assertEquals(404, status(() -> service.add(9L, 30)));
        verify(sessions, never()).save(any());
    }
}
