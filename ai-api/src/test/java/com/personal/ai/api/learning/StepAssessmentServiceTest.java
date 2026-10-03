package com.personal.ai.api.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.DiagnosticQuestion;
import com.personal.ai.agent.evaluator.DiagnosticQuiz;
import com.personal.ai.agent.evaluator.EvaluatorAgent;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import com.personal.ai.data.learning.LearningSubject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class StepAssessmentServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LearningStepRepository steps = mock(LearningStepRepository.class);
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final EvaluatorAgent evaluator = mock(EvaluatorAgent.class);
    private final StepProgressService progress = new StepProgressService(steps, mock(DashboardService.class));
    private final StepAssessmentService service = new StepAssessmentService(steps, assessments, progress, evaluator, mapper,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final LearningGoal goal = new LearningGoal(new LearningSubject("Vue", null), "실무", Level.INTERMEDIATE, null);
    private final DiagnosticQuiz quiz = new DiagnosticQuiz("1", List.of(
            new DiagnosticQuestion(1, "Promise", "CONCEPT", 2, "Promise란?", List.of("비동기"))));

    private LearningStep step(StepStatus status) {
        LearningStep s = new LearningStep(goal, 1, "비동기", "Promise를 쓴다", 2, status,
                "{\"practiceTasks\":[\"fetch 과제\"]}");
        when(steps.findWithGoalById(1L)).thenReturn(Optional.of(s));
        when(steps.findById(1L)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void createsQuizForLearningStepAndMovesItToAssessment() {
        LearningStep s = step(StepStatus.LEARNING);
        when(assessments.findFirstByStepIdAndTypeOrderByIdDesc(1L, AssessmentType.STEP)).thenReturn(Optional.empty());
        when(evaluator.generateStepQuiz(eq("Vue"), eq("실무"), eq("비동기"), eq("Promise를 쓴다"), anyInt(), eq(List.of("fetch 과제"))))
                .thenReturn(quiz);
        when(assessments.save(any())).thenAnswer(i -> i.getArgument(0));

        DiagnosticResponse response = service.open(1L);

        assertEquals(1, response.quiz().questions().size());
        assertEquals(StepStatus.ASSESSMENT, s.getStatus());
        verify(assessments).save(any(Assessment.class));
    }

    @Test
    void resumesUnansweredQuizWithoutCallingTheModelAgain() throws Exception {
        step(StepStatus.ASSESSMENT);
        Assessment pending = new Assessment(goal, null, AssessmentType.STEP, mapper.writeValueAsString(quiz));
        when(assessments.findFirstByStepIdAndTypeOrderByIdDesc(1L, AssessmentType.STEP)).thenReturn(Optional.of(pending));

        DiagnosticResponse response = service.open(1L);

        assertEquals("Promise란?", response.quiz().questions().get(0).question());
        verify(evaluator, never()).generateStepQuiz(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void rejectsStepsThatAreNotBeingLearnedBeforeCallingTheModel() {
        step(StepStatus.AVAILABLE);
        when(assessments.findFirstByStepIdAndTypeOrderByIdDesc(1L, AssessmentType.STEP)).thenReturn(Optional.empty());

        var e = assertThrows(ResponseStatusException.class, () -> service.open(1L));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(evaluator, never()).generateStepQuiz(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void unknownStepIs404() {
        when(steps.findWithGoalById(9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.open(9L)).getStatusCode());
    }
}
