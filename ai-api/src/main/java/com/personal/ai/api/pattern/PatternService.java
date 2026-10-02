package com.personal.ai.api.pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.codingtest.ReviewContent;
import com.personal.ai.agent.evaluator.DiagnosticQuestion;
import com.personal.ai.agent.evaluator.DiagnosticQuiz;
import com.personal.ai.agent.pattern.*;
import com.personal.ai.api.learning.LlmCalls;
import com.personal.ai.api.pattern.PatternDtos.*;
import com.personal.ai.core.learning.PatternStatus;
import com.personal.ai.data.codingtest.CodingProblem;
import com.personal.ai.data.codingtest.CodingSubmission;
import com.personal.ai.data.codingtest.CodingSubmissionRepository;
import com.personal.ai.data.learning.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 아직 분석하지 않은 진단 답변과 코딩 풀이에서 사고 패턴 가설을 찾아 근거와 함께 저장한다.
 * LLM 호출 동안은 DB 트랜잭션을 잡지 않고, 결과 저장과 "분석함" 표시는 하나의 트랜잭션으로 묶는다.
 * 그래서 호출이 실패하면 항목은 분석 전 상태로 남아 다시 시도할 수 있다.
 */
@Service
public class PatternService {

    /** 프롬프트가 지나치게 커지지 않도록 분석에 쓰는 코드 길이를 제한한다. 에이전트는 이 잘린 글을 기준으로 근거를 검증한다. */
    static final int MAX_CODE_CHARS_IN_PROMPT = 4000;
    private static final String ANSWER = "A";
    private static final String SUBMISSION = "S";

    private final LearningAnswerRepository answers;
    private final CodingSubmissionRepository submissions;
    private final ThinkingPatternRepository patterns;
    private final PatternEvidenceRepository evidence;
    private final PatternAnalyzerAgent agent;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public PatternService(LearningAnswerRepository answers, CodingSubmissionRepository submissions,
                          ThinkingPatternRepository patterns, PatternEvidenceRepository evidence,
                          PatternAnalyzerAgent agent, ObjectMapper mapper, TransactionTemplate tx) {
        this.answers = answers;
        this.submissions = submissions;
        this.patterns = patterns;
        this.evidence = evidence;
        this.agent = agent;
        this.mapper = mapper;
        this.tx = tx;
    }

    public AnalysisResponse analyze() {
        List<LearningAnswer> answerBatch = answers.findTop30ByAnalyzedAtIsNullOrderByIdAsc();
        List<CodingSubmission> submissionBatch = submissions.findTop10ByAnalyzedAtIsNullOrderByIdAsc();
        if (answerBatch.isEmpty() && submissionBatch.isEmpty()) {
            return new AnalysisResponse(0, 0, 0, 0, "새로 분석할 답변이나 코딩 풀이가 없습니다. 진단 답변이나 코딩 풀이가 쌓이면 다시 분석하세요.");
        }

        List<ThinkingPattern> existing = patterns.findAll();
        List<KnownPattern> known = existing.stream().filter(p -> p.getStatus() != PatternStatus.DISMISSED)
                .map(p -> new KnownPattern(p.getName(), p.getDescription())).toList();
        List<String> dismissed = existing.stream().filter(p -> p.getStatus() == PatternStatus.DISMISSED)
                .map(ThinkingPattern::getName).toList();

        List<AnalysisItem> items = new ArrayList<>(answerItems(answerBatch));
        submissionBatch.forEach(s -> items.add(submissionItem(s)));
        PatternAnalysis analysis = LlmCalls.run(() -> agent.analyze(items, known, dismissed));

        int[] counts = new int[2]; // [새 패턴, 갱신된 패턴]
        tx.executeWithoutResult(status -> {
            for (PatternObservation obs : analysis.observations()) {
                apply(obs, counts);
            }
            answerBatch.forEach(a -> answers.getReferenceById(a.getId()).markAnalyzed());
            submissionBatch.forEach(s -> submissions.getReferenceById(s.getId()).markAnalyzed());
            recompute();
        });
        return new AnalysisResponse(answerBatch.size(), submissionBatch.size(), counts[0], counts[1], null);
    }

    public List<PatternView> list() {
        return patterns.findAllByOrderByStatusDescConfidenceDescIdAsc().stream().map(this::view).toList();
    }

    /** 사용자가 패턴을 기각(DISMISSED)하거나 되살린다(HYPOTHESIS → 근거 개수에 맞는 상태로). */
    public PatternView update(Long id, UpdateRequest request) {
        PatternStatus target;
        try {
            target = PatternStatus.valueOf(String.valueOf(request.status()).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status는 DISMISSED(기각) 또는 HYPOTHESIS(되살리기)여야 합니다.");
        }
        if (target == PatternStatus.SUPPORTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUPPORTED는 근거 개수로 자동 결정됩니다. DISMISSED 또는 HYPOTHESIS만 지정할 수 있습니다.");
        }
        return tx.execute(status -> {
            ThinkingPattern p = patterns.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "패턴을 찾을 수 없습니다: " + id));
            p.setStatus(target == PatternStatus.DISMISSED ? PatternStatus.DISMISSED : PatternConfidence.status(p.getEvidenceCount()));
            return view(p);
        });
    }

    private void apply(PatternObservation obs, int[] counts) {
        ThinkingPattern pattern = patterns.findByNameIgnoreCase(obs.name()).orElse(null);
        if (pattern != null && pattern.getStatus() == PatternStatus.DISMISSED) {
            return;
        }
        if (pattern == null) {
            pattern = patterns.save(new ThinkingPattern(obs.name(), obs.description(), obs.improvementStrategy()));
            counts[0]++;
        } else {
            if (pattern.getDescription() == null || pattern.getDescription().isBlank()) {
                pattern.setDescription(obs.description());
            }
            pattern.setImprovementStrategy(obs.improvementStrategy());
            counts[1]++;
        }
        pattern.observedNow();
        for (PatternEvidenceItem item : obs.evidence()) {
            saveEvidence(pattern, item);
        }
    }

    /** itemId는 에이전트가 우리가 준 목록에서만 돌려주므로 "A12" 또는 "S3" 형식이 보장된다. */
    private void saveEvidence(ThinkingPattern pattern, PatternEvidenceItem item) {
        long id = Long.parseLong(item.itemId().substring(1));
        if (item.itemId().startsWith(SUBMISSION)) {
            if (!evidence.existsByPatternIdAndSubmissionId(pattern.getId(), id)) {
                evidence.save(PatternEvidence.fromSubmission(pattern, submissions.getReferenceById(id), item.quote(), item.note()));
            }
        } else if (!evidence.existsByPatternIdAndAnswerId(pattern.getId(), id)) {
            evidence.save(new PatternEvidence(pattern, answers.getReferenceById(id), item.quote(), item.note()));
        }
    }

    /** 분석한 항목(답변 + 코딩 풀이) 수가 늘면 모든 패턴의 비율이 바뀌므로 기각되지 않은 패턴을 전부 다시 계산한다. */
    private void recompute() {
        int total = (int) (answers.countByAnalyzedAtIsNotNull() + submissions.countByAnalyzedAtIsNotNull());
        for (ThinkingPattern p : patterns.findByStatusNot(PatternStatus.DISMISSED)) {
            int support = evidence.countByPatternId(p.getId());
            p.updateEvidence(support, PatternConfidence.confidence(support, total));
            p.setStatus(PatternConfidence.status(support));
        }
    }

    private PatternView view(ThinkingPattern p) {
        List<Evidence> recent = evidence.findTop5ByPatternIdOrderByIdDesc(p.getId()).stream().map(e ->
                e.getSubmission() != null
                        ? new Evidence("SUBMISSION", e.getSubmission().getId(), "코딩 풀이: " + e.getSubmission().getProblem().getTitle(), e.getQuote(), e.getNote())
                        : new Evidence("ANSWER", e.getAnswer().getId(), "진단 답변", e.getQuote(), e.getNote())).toList();
        return new PatternView(p.getId(), p.getName(), p.getDescription(), p.getStatus().name(), p.getConfidence(),
                p.getEvidenceCount(), p.getFirstObservedAt(), p.getLastObservedAt(), p.getImprovementStrategy(), recent);
    }

    /** 답변에 문제 내용과 채점 결과를 붙인다. 진단 문제 JSON은 진단별로 한 번만 읽는다. */
    private List<AnalysisItem> answerItems(List<LearningAnswer> batch) {
        Map<Long, Map<Integer, DiagnosticQuestion>> questionsByAssessment = new HashMap<>();
        List<AnalysisItem> items = new ArrayList<>();
        for (LearningAnswer a : batch) {
            Map<Integer, DiagnosticQuestion> questions = questionsByAssessment.computeIfAbsent(
                    a.getAssessment().getId(), id -> readQuestions(a.getAssessment().getQuestions()));
            DiagnosticQuestion q = questions.get(a.getQuestionId());
            JsonNode scores = readTree(a.getScores());
            items.add(new AnalysisItem(ANSWER + a.getId(), "ANSWER",
                    q == null ? "" : q.area(), q == null ? "" : q.type(), q == null ? 0 : q.difficulty(),
                    q == null ? "" : q.question(), a.getAnswerText(),
                    scores.hasNonNull("score") ? scores.path("score").asInt() : null,
                    scores.path("feedback").asText("")));
        }
        return items;
    }

    /**
     * 코딩 제출을 분석 항목으로 만든다. answer에는 학습자가 쓴 글(접근 설명, 코드)만 넣고,
     * AI의 풀이 리뷰는 feedback에 따로 둔다(맥락용이며 근거 인용으로 쓸 수 없다).
     */
    private AnalysisItem submissionItem(CodingSubmission s) {
        CodingProblem problem = s.getProblem();
        String question = problem.getTitle() + (problem.getDescriptionSummary() == null ? "" : " — " + problem.getDescriptionSummary());
        String code = s.getCode().length() > MAX_CODE_CHARS_IN_PROMPT ? s.getCode().substring(0, MAX_CODE_CHARS_IN_PROMPT) : s.getCode();
        String learnerText = (s.getExplanation() == null || s.getExplanation().isBlank() ? "" : "[접근 설명] " + s.getExplanation() + "\n")
                + "[코드] " + code;
        return new AnalysisItem(SUBMISSION + s.getId(), "CODE",
                problem.getCategory() == null ? "코딩테스트" : problem.getCategory(), "CODING", 0, question, learnerText,
                null, reviewDigest(s.getReview()));
    }

    /** AI 리뷰에서 어떤 차이와 실수가 지적됐는지만 짧게 요약한다. */
    private String reviewDigest(String json) {
        if (json == null) {
            return "";
        }
        try {
            ReviewContent r = mapper.readValue(json, ReviewContent.class);
            String differences = r.comparisons().stream().filter(c -> "DIFFERENT".equals(c.status()))
                    .map(c -> c.item() + ": " + cap(c.reason(), 80)).collect(Collectors.joining("; "));
            return cap("AI 리뷰 — 내 풀이: " + r.userApproach() + " / 권장: " + r.recommendedApproach()
                    + " / 다른 점: " + differences + " / 지적한 실수: " + String.join("; ", r.mistakes())
                    + " / 총평: " + r.summary(), 1200);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private static String cap(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private Map<Integer, DiagnosticQuestion> readQuestions(String json) {
        try {
            DiagnosticQuiz quiz = mapper.readValue(json, DiagnosticQuiz.class);
            return quiz.questions().stream().collect(Collectors.toMap(DiagnosticQuestion::id, q -> q, (a, b) -> a));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 진단 문제를 읽을 수 없습니다.", e);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return json == null ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }
}
