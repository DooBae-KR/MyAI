package com.personal.ai.api.stats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.codingtest.ReviewContent;
import com.personal.ai.agent.evaluator.AreaScore;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.api.learning.StepProgressService;
import com.personal.ai.api.pattern.PatternTrend;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.EvidenceKind;
import com.personal.ai.core.learning.PatternStatus;
import com.personal.ai.data.codingtest.CodingSubmissionRepository;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningSessionRepository;
import com.personal.ai.data.learning.PatternEvidence;
import com.personal.ai.data.learning.PatternEvidenceRepository;
import com.personal.ai.data.learning.ThinkingPatternRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 통계 확장 조회. LLM을 호출하지 않고 이미 저장된 점수·리뷰·근거만 집계한다. */
@Service
public class InsightsService {

    private static final Logger log = LoggerFactory.getLogger(InsightsService.class);
    private static final Pattern SPLIT = Pattern.compile("[,/·]");
    static final int MAX_AREA_CHANGES = 8;

    private final AssessmentRepository assessments;
    private final CodingSubmissionRepository submissions;
    private final LearningSessionRepository sessions;
    private final ThinkingPatternRepository patterns;
    private final PatternEvidenceRepository evidence;
    private final ObjectMapper mapper;

    public InsightsService(AssessmentRepository assessments, CodingSubmissionRepository submissions,
                           LearningSessionRepository sessions, ThinkingPatternRepository patterns,
                           PatternEvidenceRepository evidence, ObjectMapper mapper) {
        this.assessments = assessments;
        this.submissions = submissions;
        this.sessions = sessions;
        this.patterns = patterns;
        this.evidence = evidence;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public InsightsResponse insights() {
        return new InsightsResponse(stepAssessments(), algorithms(),
                submissions.countByLanguage().stream().map(r -> new StatsResponse.Count(String.valueOf(r[0]), ((Number) r[1]).longValue())).toList(),
                sessions.secondsBySubject().stream().map(r -> new StatsResponse.Count(String.valueOf(r[0]), Math.round(((Number) r[1]).longValue() / 60.0))).toList(),
                areaChanges(), patternChanges());
    }

    private InsightsResponse.Assessments stepAssessments() {
        int attempts = 0, passed = 0;
        long total = 0;
        for (Assessment a : assessments.findByTypeAndResultIsNotNullOrderByIdAsc(AssessmentType.STEP)) {
            GradingResult r = grading(a);
            if (r == null) continue;
            attempts++;
            total += r.correctness();
            if (r.correctness() >= StepProgressService.PASS_SCORE) passed++;
        }
        return new InsightsResponse.Assessments(attempts, passed, attempts - passed,
                attempts == 0 ? null : (int) Math.round((double) total / attempts));
    }

    /** 문제의 알고리즘(쉼표/슬래시로 여러 개면 각각)별로, 풀이 비교에서 권장 접근과 같다(SAME)고 본 항목의 비율. UNKNOWN은 뺀다. */
    private List<InsightsResponse.Algorithm> algorithms() {
        record Acc(int submissions, int same, int compared) {}
        Map<String, Acc> byAlgorithm = new LinkedHashMap<>();
        for (Object[] row : submissions.reviewsWithAlgorithm()) {
            ReviewContent review;
            try {
                review = mapper.readValue((String) row[1], ReviewContent.class);
            } catch (JsonProcessingException e) {
                log.warn("통계에서 풀이 비교를 읽지 못해 건너뜁니다.");
                continue;
            }
            int same = (int) review.comparisons().stream().filter(c -> "SAME".equals(c.status())).count();
            int differ = (int) review.comparisons().stream().filter(c -> "DIFFERENT".equals(c.status())).count();
            String raw = row[0] == null ? "" : row[0].toString();
            List<String> names = new ArrayList<>();
            for (String part : SPLIT.split(raw)) if (!part.isBlank()) names.add(part.trim());
            if (names.isEmpty()) names.add("미분류");
            for (String n : names) {
                byAlgorithm.merge(n, new Acc(1, same, same + differ),
                        (a, b) -> new Acc(a.submissions + b.submissions, a.same + b.same, a.compared + b.compared));
            }
        }
        return byAlgorithm.entrySet().stream()
                .map(e -> new InsightsResponse.Algorithm(e.getKey(), e.getValue().submissions, e.getValue().same, e.getValue().compared,
                        e.getValue().compared == 0 ? 0 : (int) Math.round(100.0 * e.getValue().same / e.getValue().compared)))
                .sorted(Comparator.comparingInt(InsightsResponse.Algorithm::submissions).reversed()
                        .thenComparing(InsightsResponse.Algorithm::name)).toList();
    }

    /** 진단이 2번 이상인 목표만: 첫 진단과 최근 진단에 모두 있는 영역의 점수 변화(많이 오른 순, 최대 8개). */
    private List<InsightsResponse.AreaChange> areaChanges() {
        Map<Long, List<Assessment>> byGoal = new LinkedHashMap<>();
        for (Assessment a : assessments.findByTypeAndResultIsNotNullOrderByIdAsc(AssessmentType.DIAGNOSTIC)) {
            byGoal.computeIfAbsent(a.getGoal().getId(), k -> new ArrayList<>()).add(a);
        }
        List<InsightsResponse.AreaChange> changes = new ArrayList<>();
        for (List<Assessment> list : byGoal.values()) {
            if (list.size() < 2) continue;
            GradingResult first = grading(list.get(0)), latest = grading(list.get(list.size() - 1));
            if (first == null || latest == null) continue;
            Map<String, Integer> before = new LinkedHashMap<>();
            for (AreaScore s : first.areaScores()) before.put(s.area(), s.score());
            for (AreaScore s : latest.areaScores()) {
                Integer b = before.get(s.area());
                if (b != null) {
                    changes.add(new InsightsResponse.AreaChange(list.get(0).getGoal().getSubject().getName(), s.area(), b, s.score(), s.score() - b));
                }
            }
        }
        return changes.stream().sorted(Comparator.comparingInt(InsightsResponse.AreaChange::delta).reversed())
                .limit(MAX_AREA_CHANGES).toList();
    }

    private List<InsightsResponse.PatternChange> patternChanges() {
        return patterns.findAllByOrderByStatusDescConfidenceDescIdAsc().stream()
                .filter(p -> p.getStatus() != PatternStatus.DISMISSED)
                .map(p -> new InsightsResponse.PatternChange(p.getName(), p.getStatus().name(), p.getEvidenceCount(),
                        evidence.countByPatternIdAndKind(p.getId(), EvidenceKind.IMPROVED),
                        PatternTrend.of(evidence.findTop5ByPatternIdOrderByIdDesc(p.getId()).stream().map(PatternEvidence::getKind).toList())))
                .toList();
    }

    private GradingResult grading(Assessment a) {
        try {
            return mapper.readValue(a.getResult(), GradingResult.class);
        } catch (JsonProcessingException e) {
            log.warn("통계에서 채점 결과를 읽지 못해 건너뜁니다: assessment {}", a.getId());
            return null;
        }
    }
}
