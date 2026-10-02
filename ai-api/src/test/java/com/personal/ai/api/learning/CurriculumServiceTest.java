package com.personal.ai.api.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.curriculum.*;
import com.personal.ai.agent.evaluator.*;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CurriculumServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LearningGoalRepository goals = mock(LearningGoalRepository.class);
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final LearningStepRepository steps = mock(LearningStepRepository.class);
    private final CurriculumAgent agent = mock(CurriculumAgent.class);
    private final CurriculumService service = new CurriculumService(goals, assessments, steps, agent, mapper,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private LearningGoal goal() {
        LearningSubject js = new LearningSubject("JavaScript", null);
        LearningSubject vue = new LearningSubject("Vue", null);
        vue.getPrerequisites().add(js);
        return new LearningGoal(vue, "실무", Level.INTERMEDIATE, null);
    }

    private Assessment gradedDiagnostic(LearningGoal goal) throws Exception {
        Assessment a = new Assessment(goal, null, AssessmentType.DIAGNOSTIC, "{}");
        a.setResult(mapper.writeValueAsString(new GradingResult("1", 35, new Criteria(1, 2, 3, 4, 5, 6),
                List.of(new AreaScore("비동기", 30)), Level.BEGINNER, List.of("개념"), List.of("설명 부족"), List.of())));
        return a;
    }

    @Test
    void createsStepsFirstAvailableRestLockedUsingDiagnosis() throws Exception {
        LearningGoal goal = goal();
        when(goals.findWithSubjectById(1L)).thenReturn(Optional.of(goal));
        when(assessments.findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(1L, AssessmentType.DIAGNOSTIC))
                .thenReturn(Optional.of(gradedDiagnostic(goal)));
        when(agent.plan(any())).thenReturn(new CurriculumPlan("1", List.of(
                new PlannedStep(1, "비동기", "Promise 이해", 2, 120, List.of("fetch 실습")),
                new PlannedStep(2, "컴포넌트", "컴포넌트 작성", 3, 90, List.of("버튼")),
                new PlannedStep(3, "프로젝트", "종합", 4, 300, List.of("앱")))));
        when(steps.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        CurriculumResponse response = service.create(1L);

        ArgumentCaptor<CurriculumRequest> input = ArgumentCaptor.forClass(CurriculumRequest.class);
        verify(agent).plan(input.capture());
        assertEquals(Level.BEGINNER, input.getValue().currentLevel()); // 진단으로 판정한 수준
        assertEquals(Level.INTERMEDIATE, input.getValue().targetLevel());
        assertEquals(List.of("JavaScript"), input.getValue().prerequisites());
        assertEquals(List.of("설명 부족"), input.getValue().weaknesses());

        assertEquals(List.of(StepStatus.AVAILABLE, StepStatus.LOCKED, StepStatus.LOCKED),
                response.steps().stream().map(CurriculumResponse.Step::status).toList());
        assertEquals(List.of(1, 2, 3), response.steps().stream().map(CurriculumResponse.Step::seq).toList());
        assertEquals(120, response.steps().get(0).estimatedMinutes());
        assertEquals("1", response.promptVersion());

        ArgumentCaptor<List<LearningStep>> saved = ArgumentCaptor.forClass(List.class);
        verify(steps).saveAll(saved.capture());
        assertTrue(saved.getValue().get(0).getDetail().contains("\"estimatedMinutes\":120"));
    }

    @Test
    void rejectsMissingGoalExistingCurriculumAndMissingDiagnosis() throws Exception {
        when(goals.findWithSubjectById(404L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, status(404L));

        LearningGoal goal = goal();
        when(goals.findWithSubjectById(1L)).thenReturn(Optional.of(goal));
        when(steps.existsByGoalId(1L)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, status(1L));

        when(steps.existsByGoalId(1L)).thenReturn(false);
        when(assessments.findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(1L, AssessmentType.DIAGNOSTIC))
                .thenReturn(Optional.empty());
        assertEquals(HttpStatus.CONFLICT, status(1L));

        verifyNoInteractions(agent);
        verify(steps, never()).saveAll(any());
    }

    private HttpStatus status(Long goalId) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, () -> service.create(goalId))
                .getStatusCode().value());
    }
}
