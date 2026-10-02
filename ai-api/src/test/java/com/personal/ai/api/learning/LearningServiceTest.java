package com.personal.ai.api.learning;

import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningGoalRepository;
import com.personal.ai.data.learning.LearningSubject;
import com.personal.ai.data.learning.LearningSubjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LearningServiceTest {

    private final LearningSubjectRepository subjects = mock(LearningSubjectRepository.class);
    private final LearningGoalRepository goals = mock(LearningGoalRepository.class);
    private final LearningService service = new LearningService(subjects, goals);

    LearningServiceTest() {
        when(subjects.save(any())).thenAnswer(i -> i.getArgument(0));
        when(goals.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createsSubjectWhenMissingAndTrimsInput() {
        when(subjects.findByNameIgnoreCase("Vue")).thenReturn(Optional.empty());

        SubjectGoalResponse response = service.createSubjectGoal(
                new CreateSubjectRequest(" Vue ", " 실무 수준까지 배우기 ", null, null));

        assertEquals("Vue", response.subject());
        assertEquals("실무 수준까지 배우기", response.goal());
        verify(subjects).save(any(LearningSubject.class));
        verify(goals).save(any(LearningGoal.class));
    }

    @Test
    void reusesExistingSubject() {
        when(subjects.findByNameIgnoreCase("vue")).thenReturn(Optional.of(new LearningSubject("Vue", null)));

        SubjectGoalResponse response = service.createSubjectGoal(new CreateSubjectRequest("vue", "목표", null, null));

        assertEquals("Vue", response.subject());
        verify(subjects, never()).save(any());
    }

    @Test
    void rejectsBlankSubjectOrGoal() {
        assertThrows(ResponseStatusException.class,
                () -> service.createSubjectGoal(new CreateSubjectRequest(" ", "목표", null, null)));
        assertThrows(ResponseStatusException.class,
                () -> service.createSubjectGoal(new CreateSubjectRequest("Vue", null, null, null)));
        verifyNoInteractions(goals);
    }
}
