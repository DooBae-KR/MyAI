package com.personal.ai.api.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.codingtest.ReviewContent;
import com.personal.ai.agent.evaluator.AreaScore;
import com.personal.ai.agent.evaluator.Criteria;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.EvidenceKind;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.PatternStatus;
import com.personal.ai.data.codingtest.CodingSubmissionRepository;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningSessionRepository;
import com.personal.ai.data.learning.LearningSubject;
import com.personal.ai.data.learning.PatternEvidence;
import com.personal.ai.data.learning.PatternEvidenceRepository;
import com.personal.ai.data.learning.ThinkingPattern;
import com.personal.ai.data.learning.ThinkingPatternRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InsightsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final CodingSubmissionRepository submissions = mock(CodingSubmissionRepository.class);
    private final LearningSessionRepository sessions = mock(LearningSessionRepository.class);
    private final ThinkingPatternRepository patterns = mock(ThinkingPatternRepository.class);
    private final PatternEvidenceRepository evidence = mock(PatternEvidenceRepository.class);
    private final InsightsService service = new InsightsService(assessments, submissions, sessions, patterns, evidence, mapper);

    private final LearningGoal goal = goal(1L, "Vue");

    private static LearningGoal goal(long id, String subject) {
        LearningGoal g = new LearningGoal(new LearningSubject(subject, null), "실무", Level.INTERMEDIATE, null);
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }

    private Assessment graded(LearningGoal g, AssessmentType type, int correctness, AreaScore... areas) throws Exception {
        Assessment a = new Assessment(g, null, type, "{}");
        a.setResult(mapper.writeValueAsString(new GradingResult("1", correctness, new Criteria(1, 2, 3, 4, 5, 6),
                List.of(areas), Level.BEGINNER, List.of(), List.of(), List.of())));
        return a;
    }

    private String review(String... statuses) throws Exception {
        var comps = java.util.Arrays.stream(statuses).map(s -> new ReviewContent.Comparison("항목", "m", "r", s, "why")).toList();
        return mapper.writeValueAsString(new ReviewContent("1", "내 풀이", "권장", comps, List.of(), List.of(), List.of(), "총평"));
    }

    @Test
    void emptyDataGivesEmptyInsightsWithoutAverage() {
        var r = service.insights();

        assertEquals(0, r.assessments().attempts());
        assertNull(r.assessments().averageScore());
        assertEquals(List.of(), r.algorithms());
        assertEquals(List.of(), r.areaChanges());
    }

    @Test
    void stepAssessmentsGiveAverageScorePassCountAndRelearnCount() throws Exception {
        when(assessments.findByTypeAndResultIsNotNullOrderByIdAsc(AssessmentType.STEP)).thenReturn(List.of(
                graded(goal, AssessmentType.STEP, 50), graded(goal, AssessmentType.STEP, 70), graded(goal, AssessmentType.STEP, 90)));

        var a = service.insights().assessments();

        assertEquals(new InsightsResponse.Assessments(3, 2, 1, 70), a);
    }

    @Test
    void algorithmMatchRateCountsSameOverSameAndDifferentIgnoringUnknownAndSplitsMultipleAlgorithms() throws Exception {
        when(submissions.reviewsWithAlgorithm()).thenReturn(List.of(
                new Object[] {"BFS", review("SAME", "SAME", "DIFFERENT", "UNKNOWN")},   // 2/3
                new Object[] {"BFS, 큐", review("SAME", "DIFFERENT")},                    // BFS +1/2, 큐 1/2
                new Object[] {null, review("DIFFERENT")},                                 // 미분류 0/1
                new Object[] {"DFS", "깨진 JSON"}));                                      // 건너뜀

        var algos = service.insights().algorithms();

        assertEquals("BFS", algos.get(0).name());
        assertEquals(new InsightsResponse.Algorithm("BFS", 2, 3, 5, 60), algos.get(0));
        assertEquals(0, algos.stream().filter(x -> x.name().equals("DFS")).count()); // 읽을 수 없는 리뷰는 건너뜀
        assertEquals(50, algos.stream().filter(x -> x.name().equals("큐")).findFirst().orElseThrow().matchPercent());
        assertEquals(0, algos.stream().filter(x -> x.name().equals("미분류")).findFirst().orElseThrow().matchPercent());
    }

    @Test
    void areaChangesCompareFirstAndLatestDiagnosticOnlyWhenThereAreTwo() throws Exception {
        LearningGoal other = goal(2L, "React");
        when(assessments.findByTypeAndResultIsNotNullOrderByIdAsc(AssessmentType.DIAGNOSTIC)).thenReturn(List.of(
                graded(goal, AssessmentType.DIAGNOSTIC, 30, new AreaScore("비동기", 30), new AreaScore("DOM", 80), new AreaScore("옛 영역", 10)),
                graded(other, AssessmentType.DIAGNOSTIC, 50, new AreaScore("상태", 50)),            // 진단 1번뿐 → 제외
                graded(goal, AssessmentType.DIAGNOSTIC, 60, new AreaScore("비동기", 75), new AreaScore("DOM", 70), new AreaScore("새 영역", 90))));

        var changes = service.insights().areaChanges();

        assertEquals(List.of(new InsightsResponse.AreaChange("Vue", "비동기", 30, 75, 45),
                new InsightsResponse.AreaChange("Vue", "DOM", 80, 70, -10)), changes); // 한쪽에만 있는 영역은 제외
    }

    @Test
    void patternChangesUseOnlyActivePatternsAndCodeComputedTrend() {
        ThinkingPattern active = new ThinkingPattern("압축 경향", "d", "s");
        ReflectionTestUtils.setField(active, "id", 7L);
        active.updateEvidence(3, 0.3);
        active.setStatus(PatternStatus.SUPPORTED);
        ThinkingPattern dismissed = new ThinkingPattern("기각", "d", "s");
        dismissed.setStatus(PatternStatus.DISMISSED);
        when(patterns.findAllByOrderByStatusDescConfidenceDescIdAsc()).thenReturn(List.of(active, dismissed));
        when(evidence.countByPatternIdAndKind(7L, EvidenceKind.IMPROVED)).thenReturn(2);
        PatternEvidence improved = mock(PatternEvidence.class);
        when(improved.getKind()).thenReturn(EvidenceKind.IMPROVED);
        when(evidence.findTop5ByPatternIdOrderByIdDesc(any())).thenReturn(List.of(improved, improved, improved));

        var p = service.insights().patterns();

        assertEquals(List.of(new InsightsResponse.PatternChange("압축 경향", "SUPPORTED", 3, 2, "IMPROVING")), p);
    }

    @Test
    void languageAndSubjectStudyAreConvertedFromRows() {
        when(submissions.countByLanguage()).thenReturn(List.<Object[]>of(new Object[] {"Java", 4L}, new Object[] {"Python", 1L}));
        when(sessions.secondsBySubject()).thenReturn(List.<Object[]>of(new Object[] {"Vue", 5400L}, new Object[] {"React", 90L}));

        var r = service.insights();

        assertEquals(new StatsResponse.Count("Java", 4), r.languages().get(0));
        assertEquals(List.of(new StatsResponse.Count("Vue", 90), new StatsResponse.Count("React", 2)), r.studyMinutesBySubject());
    }
}
