package com.personal.ai.api.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.*;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.data.learning.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GradingServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final LearningAnswerRepository answers = mock(LearningAnswerRepository.class);
    private final EvaluatorAgent evaluator = mock(EvaluatorAgent.class);
    private final GradingService service = new GradingService(assessments, answers, evaluator, mapper,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private static final List<DiagnosticQuestion> QUESTIONS = List.of(
            new DiagnosticQuestion(1, "JS", "CONCEPT", 1, "var와 let?", List.of("스코프")),
            new DiagnosticQuestion(2, "비동기", "UNDERSTANDING", 2, "Promise란?", List.of("비동기")));

    private final LearningGoal goal = new LearningGoal(new LearningSubject("Vue", null), "실무", Level.INTERMEDIATE, null);

    private Assessment diagnostic() throws Exception {
        return new Assessment(goal, null, AssessmentType.DIAGNOSTIC,
                mapper.writeValueAsString(new DiagnosticQuiz("1", QUESTIONS)));
    }

    private ResponseStatusException failure(AnswersRequest request) {
        return assertThrows(ResponseStatusException.class, () -> service.grade(1L, request));
    }

    private static AnswersRequest answers(AnswersRequest.Item... items) {
        return new AnswersRequest(List.of(items));
    }

    @Test
    void gradesStoresAnswersWithScoresAndUpdatesGoalLevel() throws Exception {
        Assessment assessment = diagnostic();
        when(assessments.findWithGoalById(1L)).thenReturn(Optional.of(assessment));
        when(assessments.findById(1L)).thenReturn(Optional.of(assessment));
        GradingResult result = new GradingResult("1", 80, new Criteria(70, 60, 65, 60, 55, 50),
                List.of(new AreaScore("JS", 80)), Level.ADVANCED, List.of("개념"), List.of("설명"),
                List.of(new QuestionResult(1, 90, "좋음"), new QuestionResult(2, 0, "미응답")));
        when(evaluator.grade(eq("Vue"), eq("실무"), any(), any())).thenReturn(result);

        // 2번은 공백 답변 → 저장/채점 대상에서 제외
        GradingResponse response = service.grade(1L, answers(
                new AnswersRequest.Item(1, " 스코프가 다르다 "), new AnswersRequest.Item(2, "  ")));

        assertEquals(result, response.result());
        ArgumentCaptor<Map<Integer, String>> sent = ArgumentCaptor.forClass(Map.class);
        verify(evaluator).grade(any(), any(), any(), sent.capture());
        assertEquals(Map.of(1, "스코프가 다르다"), sent.getValue());

        ArgumentCaptor<LearningAnswer> saved = ArgumentCaptor.forClass(LearningAnswer.class);
        verify(answers).save(saved.capture());
        assertEquals(1, saved.getValue().getQuestionId());
        assertEquals("스코프가 다르다", saved.getValue().getAnswerText());
        assertTrue(saved.getValue().getScores().contains("\"score\":90"));
        assertTrue(assessment.getResult().contains("\"correctness\":80"));
        assertEquals(Level.ADVANCED, goal.getCurrentLevel());
    }

    @Test
    void rejectsInvalidSubmissionsWithoutCallingLlm() throws Exception {
        when(assessments.findWithGoalById(1L)).thenReturn(Optional.of(diagnostic()));

        assertEquals(HttpStatus.BAD_REQUEST, failure(answers(new AnswersRequest.Item(9, "a"))).getStatusCode()); // 없는 문제
        assertEquals(HttpStatus.BAD_REQUEST, failure(answers(new AnswersRequest.Item(null, "a"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, failure(answers(new AnswersRequest.Item(1, " "))).getStatusCode()); // 답변 없음
        assertEquals(HttpStatus.BAD_REQUEST, failure(answers(
                new AnswersRequest.Item(1, "a"), new AnswersRequest.Item(1, "b"))).getStatusCode()); // 중복
        assertEquals(HttpStatus.BAD_REQUEST, failure(answers(
                new AnswersRequest.Item(1, "x".repeat(GradingService.MAX_ANSWER_CHARS + 1)))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, failure(new AnswersRequest(null)).getStatusCode());

        verifyNoInteractions(evaluator, answers);
    }

    @Test
    void returnsNotFoundAndConflictForMissingOrAlreadyGradedAssessment() throws Exception {
        when(assessments.findWithGoalById(404L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.grade(404L, answers(new AnswersRequest.Item(1, "a")))).getStatusCode());

        Assessment graded = diagnostic();
        graded.setResult("{}");
        when(assessments.findWithGoalById(1L)).thenReturn(Optional.of(graded));
        assertEquals(HttpStatus.CONFLICT, failure(answers(new AnswersRequest.Item(1, "a"))).getStatusCode());
        verifyNoInteractions(evaluator);
    }
}
