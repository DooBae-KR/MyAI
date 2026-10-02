package com.personal.ai.agent.evaluator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.Prompt;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 진단 문제 생성. DB를 모르고 AiModel만 쓴다. 응답은 검증을 통과한 것만 돌려준다. */
public class EvaluatorAgent {

    static final Set<String> TYPES = Set.of("CONCEPT", "SYNTAX", "UNDERSTANDING", "APPLICATION",
            "PROBLEM_SOLVING", "CODE_QUALITY", "DEBUGGING");
    private static final int MIN_QUESTIONS = 3;
    private static final int MAX_QUESTIONS = 15;
    private static final int ATTEMPTS = 2;

    private final AiModel model;
    private final Prompt prompt = Prompt.load("evaluator-diagnostic");
    private final ObjectMapper mapper = new ObjectMapper();

    public EvaluatorAgent(AiModel model) {
        this.model = model;
    }

    public DiagnosticQuiz generateDiagnostic(String subject, String goal, Level targetLevel,
                                             List<String> prerequisites) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("subject", subject);
        input.put("goal", goal);
        input.put("targetLevel", targetLevel == null ? "미정" : targetLevel.name());
        input.put("prerequisites", prerequisites);

        String userPrompt;
        try {
            userPrompt = mapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        String retryNote = "";
        AgentResponseException last = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            AiRequest request = new AiRequest();
            request.setSystemPrompt(prompt.text());
            request.setUserPrompt(userPrompt + retryNote);
            request.setTemperature(0.4);
            try {
                return new DiagnosticQuiz(prompt.version(), parse(model.chat(request).getContent()));
            } catch (AgentResponseException e) {
                last = e;
                retryNote = "\n\n[이전 응답 오류] " + e.getMessage() + "\n규칙에 맞는 JSON만 다시 출력하라.";
            }
        }
        throw last;
    }

    List<DiagnosticQuestion> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new AgentResponseException("응답이 비어 있습니다.");
        }
        // 추론 모델의 <think> 블록과 ``` 코드펜스를 걷어내고 첫 '{' ~ 마지막 '}'만 본다.
        String text = content.replaceAll("(?s)<think>.*?</think>", "");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AgentResponseException("JSON 객체를 찾을 수 없습니다.");
        }

        JsonNode root;
        try {
            root = mapper.readTree(text.substring(start, end + 1));
        } catch (JsonProcessingException e) {
            throw new AgentResponseException("JSON 파싱 실패: " + e.getOriginalMessage());
        }

        JsonNode items = root.path("questions");
        if (!items.isArray() || items.size() < MIN_QUESTIONS || items.size() > MAX_QUESTIONS) {
            throw new AgentResponseException(
                    "questions는 " + MIN_QUESTIONS + "~" + MAX_QUESTIONS + "개의 배열이어야 합니다.");
        }

        List<DiagnosticQuestion> questions = new ArrayList<>();
        for (JsonNode item : items) {
            int id = questions.size() + 1; // id는 모델이 아니라 우리가 매긴다
            String area = text(item, "area");
            String question = text(item, "question");
            String type = text(item, "type").toUpperCase();
            if (area.isBlank() || question.isBlank()) {
                throw new AgentResponseException("문제 " + id + ": area와 question은 필수입니다.");
            }
            if (!TYPES.contains(type)) {
                throw new AgentResponseException("문제 " + id + ": type은 " + TYPES + " 중 하나여야 합니다.");
            }
            JsonNode difficulty = item.path("difficulty");
            if (!difficulty.isInt() || difficulty.asInt() < 1 || difficulty.asInt() > 5) {
                throw new AgentResponseException("문제 " + id + ": difficulty는 1~5 정수여야 합니다.");
            }
            List<String> keyPoints = new ArrayList<>();
            item.path("keyPoints").forEach(k -> keyPoints.add(k.asText()));
            questions.add(new DiagnosticQuestion(id, area.trim(), type, difficulty.asInt(), question.trim(), keyPoints));
        }
        return questions;
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }
}
