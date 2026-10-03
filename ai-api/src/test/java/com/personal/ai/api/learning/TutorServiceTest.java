package com.personal.ai.api.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.AreaScore;
import com.personal.ai.agent.evaluator.Criteria;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.agent.tutor.LessonContent;
import com.personal.ai.agent.tutor.LessonRequest;
import com.personal.ai.agent.tutor.TutorAgent;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.PatternStatus;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import com.personal.ai.data.learning.LearningSubject;
import com.personal.ai.data.learning.ThinkingPattern;
import com.personal.ai.data.learning.ThinkingPatternRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class TutorServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LearningStepRepository steps = mock(LearningStepRepository.class);
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final ThinkingPatternRepository patterns = mock(ThinkingPatternRepository.class);
    private final TutorAgent tutor = mock(TutorAgent.class);
    private final TutorService service = new TutorService(steps, assessments, patterns, tutor, mapper,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final LearningGoal goal = new LearningGoal(new LearningSubject("Vue", null), "실무", Level.INTERMEDIATE, null);
    private final LessonContent lesson = new LessonContent("1", "개요 문장", List.of(new LessonContent.Concept("c", "설명")),
            List.of(new LessonContent.Example("e", "js", "code", "설명")), "과제", List.of("실수"), List.of("a", "b", "c"));

    private LearningStep step(StepStatus status, String detail) {
        LearningStep s = new LearningStep(goal, 1, "비동기", "Promise를 쓴다", 2, status, detail);
        when(steps.findWithGoalById(1L)).thenReturn(Optional.of(s));
        when(steps.findById(1L)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void generatesWithWeaknessesAndActivePatternsThenStoresInsideDetailKeepingOtherKeys() throws Exception {
        LearningStep s = step(StepStatus.LEARNING, "{\"estimatedMinutes\":90,\"practiceTasks\":[\"fetch 과제\"]}");
        Assessment diag = new Assessment(goal, null, AssessmentType.DIAGNOSTIC, "{}");
        diag.setResult(mapper.writeValueAsString(new GradingResult("1", 40, new Criteria(1, 2, 3, 4, 5, 6),
                List.of(new AreaScore("비동기", 30)), Level.BEGINNER, List.of(), List.of("비동기 흐름 설명이 부족함"), List.of())));
        when(assessments.findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(any(), any())).thenReturn(Optional.of(diag));
        ThinkingPattern active = new ThinkingPattern("설명 압축 경향", "desc", "이유를 한 문장 더");
        active.setStatus(PatternStatus.HYPOTHESIS);
        ThinkingPattern dismissed = new ThinkingPattern("기각됨", "desc", null);
        dismissed.setStatus(PatternStatus.DISMISSED);
        when(patterns.findAllByOrderByStatusDescConfidenceDescIdAsc()).thenReturn(List.of(active, dismissed));
        when(tutor.lesson(any())).thenReturn(lesson);

        assertEquals(lesson, service.lesson(1L, false));

        ArgumentCaptor<LessonRequest> sent = ArgumentCaptor.forClass(LessonRequest.class);
        verify(tutor).lesson(sent.capture());
        assertEquals(List.of("fetch 과제"), sent.getValue().practiceTasks());
        assertEquals(List.of("비동기 흐름 설명이 부족함"), sent.getValue().learnerWeaknesses());
        assertEquals(List.of(new LessonRequest.Pattern("설명 압축 경향", "이유를 한 문장 더")), sent.getValue().observedPatterns());
        var stored = mapper.readTree(s.getDetail());
        assertEquals(90, stored.get("estimatedMinutes").asInt());
        assertEquals("fetch 과제", stored.get("practiceTasks").get(0).asText());
        assertEquals("개요 문장", stored.get("lesson").get("overview").asText());
    }

    @Test
    void returnsStoredLessonWithoutCallingTheModelUnlessRefreshRequested() throws Exception {
        step(StepStatus.LEARNING, "{\"lesson\":" + mapper.writeValueAsString(lesson) + "}");

        assertEquals(lesson, service.lesson(1L, false));
        verify(tutor, never()).lesson(any());

        when(assessments.findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(any(), any())).thenReturn(Optional.empty());
        when(patterns.findAllByOrderByStatusDescConfidenceDescIdAsc()).thenReturn(List.of());
        when(tutor.lesson(any())).thenReturn(lesson);
        service.lesson(1L, true);
        verify(tutor, times(1)).lesson(any());
    }

    @Test
    void lockedStepAndUnknownStepAreRejected() {
        step(StepStatus.LOCKED, "{}");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.lesson(1L, false)).getStatusCode());
        verify(tutor, never()).lesson(any());

        when(steps.findWithGoalById(9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.lesson(9L, false)).getStatusCode());
    }

    @Test
    void generatesFromScratchWhenDetailIsNull() {
        LearningStep s = step(StepStatus.AVAILABLE, null);
        when(assessments.findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(any(), any())).thenReturn(Optional.empty());
        when(patterns.findAllByOrderByStatusDescConfidenceDescIdAsc()).thenReturn(List.of());
        when(tutor.lesson(any())).thenReturn(lesson);

        service.lesson(1L, false);

        assertTrue(s.getDetail().contains("\"lesson\""));
    }
}
