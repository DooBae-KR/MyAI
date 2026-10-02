package com.personal.ai.agent.evaluator;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.Json;
import com.personal.ai.agent.Prompt;
import com.personal.ai.agent.StructuredLlm;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.model.AiModel;

import java.util.*;

/** 진단 문제 생성과 채점. DB를 모르고 AiModel만 쓴다. 응답은 검증을 통과한 것만 돌려준다. */
public class EvaluatorAgent {

    static final Set<String> TYPES = Set.of("CONCEPT", "SYNTAX", "UNDERSTANDING", "APPLICATION",
            "PROBLEM_SOLVING", "CODE_QUALITY", "DEBUGGING");
    private static final int MIN_QUESTIONS = 3;
    private static final int MAX_QUESTIONS = 15;
    /** 난이도 가중 정답률 기준. 40 미만 BEGINNER, 75 미만 INTERMEDIATE, 그 이상 ADVANCED. */
    static final int INTERMEDIATE_FROM = 40;
    static final int ADVANCED_FROM = 75;

    private final StructuredLlm llm;
    private final Prompt diagnosticPrompt = Prompt.load("evaluator-diagnostic");
    private final Prompt gradingPrompt = Prompt.load("evaluator-grading");

    public EvaluatorAgent(AiModel model) {
        this.llm = new StructuredLlm(model);
    }

    // ---- 진단 문제 생성 ----

    public DiagnosticQuiz generateDiagnostic(String subject, String goal, Level targetLevel,
                                             List<String> prerequisites) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("subject", subject);
        input.put("goal", goal);
        input.put("targetLevel", targetLevel == null ? "미정" : targetLevel.name());
        input.put("prerequisites", prerequisites);

        List<DiagnosticQuestion> questions =
                llm.call(diagnosticPrompt, llm.toJson(input), 0.4, this::parseQuestions);
        return new DiagnosticQuiz(diagnosticPrompt.version(), questions);
    }

    List<DiagnosticQuestion> parseQuestions(JsonNode root) {
        JsonNode items = root.path("questions");
        if (!items.isArray() || items.size() < MIN_QUESTIONS || items.size() > MAX_QUESTIONS) {
            throw new AgentResponseException(
                    "questions는 " + MIN_QUESTIONS + "~" + MAX_QUESTIONS + "개의 배열이어야 합니다.");
        }

        List<DiagnosticQuestion> questions = new ArrayList<>();
        for (JsonNode item : items) {
            int id = questions.size() + 1; // id는 모델이 아니라 우리가 매긴다
            String label = "문제 " + id;
            String area = Json.text(item, "area").trim();
            String question = Json.text(item, "question").trim();
            String type = Json.text(item, "type").toUpperCase();
            if (area.isBlank() || question.isBlank()) {
                throw new AgentResponseException(label + ": area와 question은 필수입니다.");
            }
            if (!TYPES.contains(type)) {
                throw new AgentResponseException(label + ": type은 " + TYPES + " 중 하나여야 합니다.");
            }
            int difficulty = Json.intInRange(item, "difficulty", 1, 5, label);
            questions.add(new DiagnosticQuestion(id, area, type, difficulty, question, Json.strings(item, "keyPoints", 6)));
        }
        return questions;
    }

    // ---- 채점 ----

    /**
     * @param answers 문제 번호 → 답변. 비었거나 없는 문제는 0점(미응답)으로 처리하고 LLM에는 보내지 않는다.
     */
    public GradingResult grade(String subject, String goal, List<DiagnosticQuestion> questions,
                               Map<Integer, String> answers) {
        List<Map<String, Object>> items = new ArrayList<>();
        Set<Integer> answered = new LinkedHashSet<>();
        for (DiagnosticQuestion q : questions) {
            String answer = answers.get(q.id());
            if (answer == null || answer.isBlank()) {
                continue;
            }
            answered.add(q.id());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", q.id());
            item.put("area", q.area());
            item.put("type", q.type());
            item.put("difficulty", q.difficulty());
            item.put("question", q.question());
            item.put("keyPoints", q.keyPoints());
            item.put("answer", answer);
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("채점할 답변이 없습니다.");
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("subject", subject);
        input.put("goal", goal);
        input.put("items", items);

        RawGrading raw = llm.call(gradingPrompt, llm.toJson(input), 0.2, root -> parseGrading(root, answered));
        return assemble(raw, questions, gradingPrompt.version());
    }

    private record RawGrading(Map<Integer, QuestionResult> results, Criteria criteria,
                              List<String> strengths, List<String> weaknesses) {}

    private RawGrading parseGrading(JsonNode root, Set<Integer> answered) {
        Map<Integer, QuestionResult> results = new LinkedHashMap<>();
        for (JsonNode item : root.path("questionResults")) {
            JsonNode idNode = item.path("questionId");
            if (!idNode.isInt() || !answered.contains(idNode.asInt())) {
                throw new AgentResponseException("questionResults에 채점 대상이 아닌 questionId가 있습니다: " + idNode);
            }
            int id = idNode.asInt();
            if (results.containsKey(id)) {
                throw new AgentResponseException("questionId " + id + "의 채점 결과가 중복되었습니다.");
            }
            results.put(id, new QuestionResult(id, Json.intInRange(item, "score", 0, 100, "문제 " + id),
                    Json.text(item, "feedback").trim()));
        }
        if (!results.keySet().equals(answered)) {
            Set<Integer> missing = new TreeSet<>(answered);
            missing.removeAll(results.keySet());
            throw new AgentResponseException("모든 답변의 채점 결과가 필요합니다. 누락된 questionId: " + missing);
        }

        JsonNode c = root.path("criteria");
        Criteria criteria = new Criteria(
                Json.intInRange(c, "conceptUnderstanding", 0, 100, "criteria"),
                Json.intInRange(c, "reasoningQuality", 0, 100, "criteria"),
                Json.intInRange(c, "problemSolving", 0, 100, "criteria"),
                Json.intInRange(c, "codeQuality", 0, 100, "criteria"),
                Json.intInRange(c, "structure", 0, 100, "criteria"),
                Json.intInRange(c, "communication", 0, 100, "criteria"));
        return new RawGrading(results, criteria, Json.strings(root, "strengths", 5), Json.strings(root, "weaknesses", 5));
    }

    /** 점수 계산은 코드가 한다: 난이도 가중 정답률, 영역별 평균, 수준 판정. 미응답은 0점. */
    private GradingResult assemble(RawGrading raw, List<DiagnosticQuestion> questions, String promptVersion) {
        List<QuestionResult> all = new ArrayList<>();
        Map<String, List<Integer>> byArea = new LinkedHashMap<>();
        long weighted = 0;
        long weights = 0;
        for (DiagnosticQuestion q : questions) {
            QuestionResult r = raw.results().getOrDefault(q.id(), new QuestionResult(q.id(), 0, "미응답"));
            all.add(r);
            byArea.computeIfAbsent(q.area(), k -> new ArrayList<>()).add(r.score());
            weighted += (long) r.score() * q.difficulty();
            weights += q.difficulty();
        }
        int correctness = (int) Math.round((double) weighted / weights);

        List<AreaScore> areaScores = new ArrayList<>();
        byArea.forEach((area, scores) -> areaScores.add(new AreaScore(area,
                (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0)))));

        Level level = correctness >= ADVANCED_FROM ? Level.ADVANCED
                : correctness >= INTERMEDIATE_FROM ? Level.INTERMEDIATE : Level.BEGINNER;
        return new GradingResult(promptVersion, correctness, raw.criteria(), areaScores, level,
                raw.strengths(), raw.weaknesses(), all);
    }
}
