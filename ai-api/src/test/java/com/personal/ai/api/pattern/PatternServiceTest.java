package com.personal.ai.api.pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.evaluator.DiagnosticQuestion;
import com.personal.ai.agent.codingtest.CodingTestAgent;
import com.personal.ai.agent.codingtest.ReviewContent;
import com.personal.ai.agent.evaluator.DiagnosticQuiz;
import com.personal.ai.agent.pattern.*;
import com.personal.ai.api.pattern.PatternDtos.*;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.PatternStatus;
import com.personal.ai.data.codingtest.*;
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
    private final CodingSubmissionRepository submissions = mock(CodingSubmissionRepository.class);
    private final ThinkingPatternRepository patterns = mock(ThinkingPatternRepository.class);
    private final PatternEvidenceRepository evidence = mock(PatternEvidenceRepository.class);
    private final PatternAnalyzerAgent agent = mock(PatternAnalyzerAgent.class);
    private final PatternService service = new PatternService(answers, submissions, patterns, evidence, agent, mapper,
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
                .mapToObj(id -> new PatternEvidenceItem("A" + id, "인용" + id + "번 답변", "근거")).toList();
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
        when(evidence.countByPatternIdAndKind(7L, com.personal.ai.core.learning.EvidenceKind.OBSERVED)).thenReturn(3);
        when(patterns.findByStatusNot(PatternStatus.DISMISSED)).thenAnswer(i -> List.of(saved[0]));

        AnalysisResponse response = service.analyze();

        assertEquals(3, response.analyzedAnswers());
        assertEquals(1, response.newPatterns());
        // 에이전트에는 답변과 문제 맥락, 채점 결과가 전달되고 채점 기준(keyPoints)은 포함되지 않는다
        ArgumentCaptor<List<AnalysisItem>> sent = ArgumentCaptor.forClass(List.class);
        verify(agent).analyze(sent.capture(), any(), any());
        AnalysisItem first = sent.getValue().get(0);
        assertEquals("A1", first.itemId());
        assertEquals("ANSWER", first.kind());
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
    void improvementsAreStoredAsImprovedEvidenceAndLeaveConfidenceAndLastObservedUntouched() throws Exception {
        LearningAnswer a5 = answer(5, 1, "이번엔 결론과 이유를 썼습니다");
        when(answers.findTop30ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of(a5));
        ThinkingPattern pattern = new ThinkingPattern("설명 압축 경향", "d", "결론, 이유, 원리 순서로 쓴다");
        ReflectionTestUtils.setField(pattern, "id", 7L);
        pattern.updateEvidence(3, 0.4);
        pattern.setStatus(PatternStatus.SUPPORTED);
        when(patterns.findAll()).thenReturn(List.of(pattern));
        when(patterns.findByNameIgnoreCase("설명 압축 경향")).thenReturn(Optional.of(pattern));
        when(patterns.findByStatusNot(PatternStatus.DISMISSED)).thenReturn(List.of(pattern));
        when(answers.getReferenceById(5L)).thenReturn(a5);
        when(answers.countByAnalyzedAtIsNotNull()).thenReturn(8L);
        when(evidence.countByPatternIdAndKind(7L, com.personal.ai.core.learning.EvidenceKind.OBSERVED)).thenReturn(3);
        when(agent.analyze(any(), any(), any())).thenReturn(new PatternAnalysis("3", List.of(), List.of(
                new PatternImprovement("설명 압축 경향", new PatternEvidenceItem("A5", "결론과 이유를 썼습니다", "이유까지 씀")))));
        var lastObservedBefore = pattern.getLastObservedAt();

        AnalysisResponse response = service.analyze();

        assertEquals(1, response.improvements());
        ArgumentCaptor<PatternEvidence> saved = ArgumentCaptor.forClass(PatternEvidence.class);
        verify(evidence).save(saved.capture());
        assertEquals(com.personal.ai.core.learning.EvidenceKind.IMPROVED, saved.getValue().getKind());
        assertEquals(3, pattern.getEvidenceCount()); // 관찰 근거 3개 그대로
        assertEquals(PatternStatus.SUPPORTED, pattern.getStatus());
        assertEquals(lastObservedBefore, pattern.getLastObservedAt());
        // 에이전트에는 훈련 제안이 개선 판단 기준으로 전달된다
        ArgumentCaptor<List<KnownPattern>> known = ArgumentCaptor.forClass(List.class);
        verify(agent).analyze(any(), known.capture(), any());
        assertEquals("결론, 이유, 원리 순서로 쓴다", known.getValue().get(0).improvementStrategy());
    }

    @Test
    void improvementsForDismissedPatternsOrAlreadyUsedItemsAreSkipped() throws Exception {
        LearningAnswer a5 = answer(5, 1, "답5");
        when(answers.findTop30ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of(a5));
        ThinkingPattern dismissed = new ThinkingPattern("기각됨", "d", "s");
        dismissed.setStatus(PatternStatus.DISMISSED);
        ThinkingPattern used = new ThinkingPattern("이미 근거", "d", "s");
        ReflectionTestUtils.setField(used, "id", 8L);
        when(patterns.findAll()).thenReturn(List.of(dismissed, used));
        when(patterns.findByNameIgnoreCase("기각됨")).thenReturn(Optional.of(dismissed));
        when(patterns.findByNameIgnoreCase("이미 근거")).thenReturn(Optional.of(used));
        when(evidence.existsByPatternIdAndAnswerId(8L, 5L)).thenReturn(true); // 이 항목은 이미 관찰 근거로 쓰였다
        when(answers.getReferenceById(5L)).thenReturn(a5);
        when(agent.analyze(any(), any(), any())).thenReturn(new PatternAnalysis("3", List.of(), List.of(
                new PatternImprovement("기각됨", new PatternEvidenceItem("A5", "인용5번 답변", "n")),
                new PatternImprovement("이미 근거", new PatternEvidenceItem("A5", "인용5번 답변", "n")))));

        assertEquals(0, service.analyze().improvements());
        verify(evidence, never()).save(any());
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

    private CodingSubmission submission(long id, String code, String explanation) throws Exception {
        CodingProblem problem = new CodingProblem("미로 탐색").details("예시", null, null, null, "Lv.2", "그래프", "BFS", "Queue", "Java", "최소 이동 횟수를 구한다.", 40);
        List<ReviewContent.Comparison> comps = CodingTestAgent.COMPARISON_ITEMS.stream()
                .map(i -> new ReviewContent.Comparison(i, "DFS", "BFS", "DIFFERENT", "최단거리이므로 BFS가 적합")).toList();
        ReviewContent review = new ReviewContent("1", "DFS로 모든 경로 탐색", "BFS 권장", comps, List.of("방문 해제로 중복 탐색"), List.of(), List.of(), "BFS를 권한다");
        CodingSubmission s = new CodingSubmission(problem, "Java", code, explanation, mapper.writeValueAsString(review));
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    @Test
    void codingSubmissionsAreAnalyzedWithLearnerTextAsAnswerAndAiReviewOnlyAsContext() throws Exception {
        CodingSubmission sub = submission(7, "dfs(nx, ny, cnt + 1);\nvisited[nx][ny] = false;", "모든 경로를 탐색하면 된다");
        LearningAnswer a1 = answer(1, 1, "비동기 결과를 담는 객체");
        when(answers.findTop30ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of(a1));
        when(submissions.findTop10ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of(sub));
        when(patterns.findAll()).thenReturn(List.of());
        when(agent.analyze(any(), any(), any())).thenReturn(new PatternAnalysis("2", List.of(new PatternObservation(
                "DFS 먼저 고르는 경향", "d", "5개 문제에서 최단거리인지 먼저 적는다.",
                List.of(new PatternEvidenceItem("S7", "dfs(nx, ny, cnt + 1);", "DFS 재귀"), new PatternEvidenceItem("A1", "비동기 결과", "압축"))))));
        ThinkingPattern[] saved = new ThinkingPattern[1];
        when(patterns.save(any())).thenAnswer(i -> {
            ThinkingPattern p = i.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 9L);
            saved[0] = p;
            return p;
        });
        when(patterns.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(answers.getReferenceById(1L)).thenReturn(a1);
        when(submissions.getReferenceById(7L)).thenReturn(sub);
        when(answers.countByAnalyzedAtIsNotNull()).thenReturn(1L);
        when(submissions.countByAnalyzedAtIsNotNull()).thenReturn(1L);
        when(evidence.countByPatternIdAndKind(9L, com.personal.ai.core.learning.EvidenceKind.OBSERVED)).thenReturn(2);
        when(patterns.findByStatusNot(PatternStatus.DISMISSED)).thenAnswer(i -> List.of(saved[0]));

        AnalysisResponse response = service.analyze();

        assertEquals(1, response.analyzedAnswers());
        assertEquals(1, response.analyzedSubmissions());
        // 에이전트에 가는 코드 항목: answer는 학습자의 글(접근 설명+코드)뿐이고, AI 리뷰는 feedback에만 있다
        ArgumentCaptor<List<AnalysisItem>> sent = ArgumentCaptor.forClass(List.class);
        verify(agent).analyze(sent.capture(), any(), any());
        AnalysisItem code = sent.getValue().stream().filter(i -> i.itemId().equals("S7")).findFirst().orElseThrow();
        assertEquals("CODE", code.kind());
        assertEquals("그래프", code.area());
        assertTrue(code.answer().contains("[접근 설명] 모든 경로를 탐색하면 된다") && code.answer().contains("[코드] dfs(nx, ny, cnt + 1);"));
        assertFalse(code.answer().contains("BFS를 권한다")); // AI 리뷰는 학습자 글에 섞이지 않는다
        assertTrue(code.feedback().contains("BFS를 권한다") && code.feedback().contains("방문 해제로 중복 탐색"));
        // 근거는 출처별로 저장되고, 둘 다 분석함 표시, 신뢰도는 (답변+코딩 풀이) 전체 수로 계산
        ArgumentCaptor<PatternEvidence> evidences = ArgumentCaptor.forClass(PatternEvidence.class);
        verify(evidence, times(2)).save(evidences.capture());
        assertEquals(1, evidences.getAllValues().stream().filter(e -> e.getSubmission() != null && e.getAnswer() == null).count());
        assertEquals(1, evidences.getAllValues().stream().filter(e -> e.getAnswer() != null && e.getSubmission() == null).count());
        assertNotNull(sub.getAnalyzedAt());
        assertNotNull(a1.getAnalyzedAt());
        assertEquals(PatternConfidence.confidence(2, 2), saved[0].getConfidence());
        assertEquals(PatternStatus.HYPOTHESIS, saved[0].getStatus()); // 근거 2개(<3)라 아직 가설
    }

    @Test
    void longCodeIsTruncatedForTheModelAndSubmissionEvidenceShowsProblemTitle() throws Exception {
        CodingSubmission sub = submission(7, "x".repeat(PatternService.MAX_CODE_CHARS_IN_PROMPT + 500), null);
        when(answers.findTop30ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of());
        when(submissions.findTop10ByAnalyzedAtIsNullOrderByIdAsc()).thenReturn(List.of(sub));
        when(patterns.findAll()).thenReturn(List.of());
        when(agent.analyze(any(), any(), any())).thenReturn(new PatternAnalysis("2", List.of()));
        when(submissions.getReferenceById(7L)).thenReturn(sub);

        service.analyze();

        ArgumentCaptor<List<AnalysisItem>> sent = ArgumentCaptor.forClass(List.class);
        verify(agent).analyze(sent.capture(), any(), any());
        assertEquals("[코드] ".length() + PatternService.MAX_CODE_CHARS_IN_PROMPT, sent.getValue().get(0).answer().length());

        // 목록에서는 코딩 풀이 근거가 문제 제목과 함께 보인다
        ThinkingPattern p = new ThinkingPattern("패턴", "d", "s");
        ReflectionTestUtils.setField(p, "id", 3L);
        when(patterns.findAllByOrderByStatusDescConfidenceDescIdAsc()).thenReturn(List.of(p));
        when(evidence.findTop5ByPatternIdOrderByIdDesc(3L)).thenReturn(List.of(PatternEvidence.fromSubmission(p, sub, "dfs(...)", "DFS")));
        Evidence e = service.list().get(0).evidence().get(0);
        assertEquals("SUBMISSION", e.source());
        assertEquals("코딩 풀이: 미로 탐색", e.label());
    }
}
