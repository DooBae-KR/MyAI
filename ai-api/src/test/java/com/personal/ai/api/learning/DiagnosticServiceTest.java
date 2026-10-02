package com.personal.ai.api.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.evaluator.DiagnosticQuestion;
import com.personal.ai.agent.evaluator.DiagnosticQuiz;
import com.personal.ai.agent.evaluator.EvaluatorAgent;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningGoalRepository;
import com.personal.ai.data.learning.LearningSubject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DiagnosticServiceTest {

    private final LearningGoalRepository goals = mock(LearningGoalRepository.class);
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final EvaluatorAgent evaluator = mock(EvaluatorAgent.class);
    private final DiagnosticService service = new DiagnosticService(goals, assessments, evaluator, new ObjectMapper());

    private LearningGoal goal() {
        LearningSubject js = new LearningSubject("JavaScript", null);
        LearningSubject vue = new LearningSubject("Vue", null);
        vue.getPrerequisites().add(js);
        return new LearningGoal(vue, "실무 수준까지", Level.INTERMEDIATE, null);
    }

    private ResponseStatusException failure(Runnable call) {
        return assertThrows(ResponseStatusException.class, call::run);
    }

    @Test
    void generatesAndStoresDiagnosticWithPromptVersion() {
        when(goals.findWithSubjectById(1L)).thenReturn(Optional.of(goal()));
        DiagnosticQuiz quiz = new DiagnosticQuiz("1",
                List.of(new DiagnosticQuestion(1, "JS", "CONCEPT", 1, "Promise란?", List.of("비동기"))));
        when(evaluator.generateDiagnostic("Vue", "실무 수준까지", Level.INTERMEDIATE, List.of("JavaScript")))
                .thenReturn(quiz);
        when(assessments.save(any())).thenAnswer(i -> i.getArgument(0));

        DiagnosticResponse response = service.generate(1L);

        assertEquals(quiz, response.quiz());
        ArgumentCaptor<Assessment> saved = ArgumentCaptor.forClass(Assessment.class);
        verify(assessments).save(saved.capture());
        assertEquals(AssessmentType.DIAGNOSTIC, saved.getValue().getType());
        assertNull(saved.getValue().getStep());
        assertTrue(saved.getValue().getQuestions().contains("\"promptVersion\":\"1\""));
        assertTrue(saved.getValue().getQuestions().contains("Promise란?"));
    }

    @Test
    void mapsFailuresToHttpStatusesAndStoresNothing() {
        when(goals.findWithSubjectById(404L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, failure(() -> service.generate(404L)).getStatusCode());

        when(goals.findWithSubjectById(1L)).thenReturn(Optional.of(goal()));
        doThrow(new AgentResponseException("형식 오류")).when(evaluator).generateDiagnostic(any(), any(), any(), any());
        assertEquals(HttpStatus.BAD_GATEWAY, failure(() -> service.generate(1L)).getStatusCode());

        doThrow(new ResourceAccessException("연결 거부")).when(evaluator).generateDiagnostic(any(), any(), any(), any());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure(() -> service.generate(1L)).getStatusCode());

        verifyNoInteractions(assessments);
    }
}
