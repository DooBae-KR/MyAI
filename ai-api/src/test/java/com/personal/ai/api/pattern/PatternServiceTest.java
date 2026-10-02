package com.personal.ai.api.pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.evaluator.DiagnosticQuestion;
import com.personal.ai.agent.evaluator.DiagnosticQuiz;
import com.personal.ai.agent.pattern.*;
import com.personal.ai.api.pattern.PatternDtos.*;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.PatternStatus;
import com.personal.ai.data.learning.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PatternServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LearningAnswerRepository answers = mock(LearningAnswerRepository.class);
    private final ThinkingPatternRepository patterns = mock(ThinkingPatternRepository.class);
    private final PatternEvidenceRepository evidence = mock(PatternEvidenceRepository.class);
    private final PatternAnalyzerAgent agent = mock(PatternAnalyzerAgent.class);
    private final PatternService service = new PatternService(answers, patterns, evidence, agent, mapper,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private LearningAnswer answer(long id, int questionId, String text) throws Exception {
        LearningGoal goal = new LearningGoal(new LearningSubject("Vue", null), "목표", Level.INTERMEDIATE, null);
        Assessment assessment = new Assessment(goal, null, AssessmentType.DIAGNOSTIC, mapper.writeValueAsString(
                new DiagnosticQuiz("1", List.of(new DiagnosticQuestion(questionId, "비동기", "CONCEPT", 2, "Promise란?", List.of("정답힌트"))))));
        ReflectionTestUtils.setField(assessment, "id", 100L);
        LearningAnswer a = new LearningAnswer(assessment, questionId, text);
        a.setScores("{\"questionId\":" + questionId + ",\"score\":60,\"feedback\":\"설명 부족\"}");
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private PatternAnalysis analysis(String name, long... answerIds) {
        List<PatternEvidenceItem> items = java.util.Arrays.stream(answerIds)
                .mapToObj(id -> new PatternEvidenceItem(id, "인용" + id + "번 답변", "근거")).toList();
        return new PatternAnalysis("1", List.of(new PatternObservation(name, "압축하는 경향이 있을 수 있다.", "10개 답변에서 4단계로 쓴다.", items)));
    }

    @Test
    void analyzesNewAnswersWithContextAndStoresPatternEvidenceAndMarksAnalyzed() throws Exception {
        LearningAnswer a1 = answer(1, 1, "답1"), a2 = answer(2, 1, "답2"), a3 = answer(3, 1, "답3");
        when(answers.findTop30ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of(a1, a2, a3));
        when(patterns.findAll()).thenReturn(List.of());
        when(agent.analyze(any(), any(), any())).thenReturn(analysis("설명 압축 경향", 1, 2, 3));
        when(patterns.findByNameIgnoreCase("설명 압축 경향")).thenReturn(Optional.empty());
        ThinkingPattern[] saved = new ThinkingPattern[1];
        when(patterns.save(any())).thenAnswer(i -> {
            ThinkingPattern p = i.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 7L);
            saved[0] = p;
            return p;
        });
        for (long id : new long[]{1, 2, 3}) {
            LearningAnswer ref = id == 1 ? a1 : id == 2 ? a2 : a3;
            when(answers.getReferenceById(id)).thenReturn(ref);
        }
        when(evidence.existsByPatternIdAndAnswerId(any(), any())).thenReturn(false);
        when(answers.countByAnalyzedAtIsNotNull()).thenReturn(3L);
        when(evidence.countByPatternId(7L)).thenReturn(3);
        when(patterns.findByStatusNot(PatternStatus.DISMISSED)).thenAnswer(i -> List.of(saved[0]));

        AnalysisResponse response = service.analyze();

        assertEquals(3, response.analyzedAnswers());
        assertEquals(1, response.newPatterns());
        // 에이전트에는 답변과 문제 맥락, 채점 결과가 전달되고 채점 기준(keyPoints)은 포함되지 않는다
        ArgumentCaptor<List<AnswerForAnalysis>> sent = ArgumentCaptor.forClass(List.class);
        verify(agent).analyze(sent.capture(), any(), any());
        AnswerForAnalysis first = sent.getValue().get(0);
        assertEquals("비동기", first.area());
        assertEquals("Promise란?", first.question());
        assertEquals(60, first.score());
        assertEquals("설명 부족", first.feedback());
        assertFalse(first.toString().contains("정답힌트"));
        // 근거 3개, 분석한 답변 3개 → 반복 관찰됨, 신뢰도는 코드가 계산
        verify(evidence, times(3)).save(any(PatternEvidence.class));
        assertNotNull(a1.getAnalyzedAt());
        assertNotNull(a3.getAnalyzedAt());
        assertEquals(3, saved[0].getEvidenceCount());
        assertEquals(PatternStatus.SUPPORTED, saved[0].getStatus());
        assertEquals(PatternConfidence.confidence(3, 3), saved[0].getConfidence());
    }

    @Test
    void doesNothingWhenThereAreNoNewAnswers() {
        when(answers.findTop30ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of());

        AnalysisResponse response = service.analyze();

        assertEquals(0, response.analyzedAnswers());
        assertNotNull(response.message());
        verifyNoInteractions(agent);
    }

    @Test
    void failedAnalysisLeavesAnswersUnanalyzedSoItCanBeRetried() throws Exception {
        LearningAnswer a1 = answer(1, 1, "답1");
        when(answers.findTop30ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of(a1));
        when(patterns.findAll()).thenReturn(List.of());
        when(agent.analyze(any(), any(), any())).thenThrow(new AgentResponseException("근거 인용이 실제 답변에 없습니다"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.analyze());

        assertEquals(HttpStatus.BAD_GATEWAY, HttpStatus.valueOf(e.getStatusCode().value()));
        assertNull(a1.getAnalyzedAt());
        verify(patterns, never()).save(any());
        verify(evidence, never()).save(any());
    }

    @Test
    void dismissedPatternsAreSentToTheAgentAndNeverRevivedByAnalysis() throws Exception {
        ThinkingPattern dismissed = new ThinkingPattern("기각한 패턴", "d", "s");
        dismissed.setStatus(PatternStatus.DISMISSED);
        LearningAnswer a1 = answer(1, 1, "답1");
        when(answers.findTop30ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of(a1));
        when(patterns.findAll()).thenReturn(List.of(dismissed));
        when(agent.analyze(any(), any(), any())).thenReturn(analysis("기각한 패턴", 1));
        when(patterns.findByNameIgnoreCase("기각한 패턴")).thenReturn(Optional.of(dismissed));
        when(answers.getReferenceById(1L)).thenReturn(a1);
        when(patterns.findByStatusNot(PatternStatus.DISMISSED)).thenReturn(List.of());

        AnalysisResponse response = service.analyze();

        ArgumentCaptor<List<String>> dismissedNames = ArgumentCaptor.forClass(List.class);
        verify(agent).analyze(any(), any(), dismissedNames.capture());
        assertEquals(List.of("기각한 패턴"), dismissedNames.getValue());
        assertEquals(0, response.newPatterns() + response.updatedPatterns());
        assertEquals(PatternStatus.DISMISSED, dismissed.getStatus());
        verify(evidence, never()).save(any());
        assertNotNull(a1.getAnalyzedAt()); // 답변은 분석한 것으로 표시된다
    }

    @Test
    void userCanDismissAndRestoreAPatternAndInvalidStatusesAreRejected() {
        ThinkingPattern p = new ThinkingPattern("패턴", "d", "s");
        p.updateEvidence(4, 0.3);
        p.setStatus(PatternStatus.SUPPORTED);
        when(patterns.findById(1L)).thenReturn(Optional.of(p));
        when(evidence.findTop5ByPatternIdOrderByIdDesc(any())).thenReturn(List.of());

        assertEquals("DISMISSED", service.update(1L, new UpdateRequest("dismissed")).status());
        assertEquals(PatternStatus.DISMISSED, p.getStatus());
        // 되살리면 근거 개수(4개)에 맞는 상태로 돌아간다
        assertEquals("SUPPORTED", service.update(1L, new UpdateRequest("HYPOTHESIS")).status());

        for (String bad : new String[]{"SUPPORTED", "DONE", null}) {
            assertEquals(HttpStatus.BAD_REQUEST, HttpStatus.valueOf(assertThrows(ResponseStatusException.class,
                    () -> service.update(1L, new UpdateRequest(bad))).getStatusCode().value()));
        }
        when(patterns.findById(404L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, HttpStatus.valueOf(assertThrows(ResponseStatusException.class,
                () -> service.update(404L, new UpdateRequest("DISMISSED"))).getStatusCode().value()));
    }
}
