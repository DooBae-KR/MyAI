package com.personal.ai.agent.pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.Json;
import com.personal.ai.agent.Prompt;
import com.personal.ai.agent.StructuredLlm;
import com.personal.ai.core.model.AiModel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 답변과 코딩 풀이에서 반복되는 사고/학습 행동을 "가설"로 제안한다. DB를 모른다.
 * LLM은 패턴과 근거 인용을 제안만 하고, 인용이 실제 답변에 있는지는 여기서 코드가 검증한다(환각 방지).
 * 신뢰도와 상태는 근거 개수로 서비스가 계산한다.
 */
public class PatternAnalyzerAgent {

    private static final int MAX_OBSERVATIONS = 5;
    private static final int MAX_EVIDENCE = 5;
    private static final int MIN_QUOTE_CHARS = 4;

    private final StructuredLlm llm;
    private final Prompt prompt = Prompt.load("pattern-analyzer-agent");

    public PatternAnalyzerAgent(AiModel model) {
        this.llm = new StructuredLlm(model);
    }

    public PatternAnalysis analyze(List<AnalysisItem> items, List<KnownPattern> known, List<String> dismissedNames) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("분석할 항목이 없습니다.");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", items);
        input.put("knownPatterns", known);
        input.put("dismissedPatterns", dismissedNames);

        Map<String, String> userTexts = items.stream()
                .collect(Collectors.toMap(AnalysisItem::itemId, a -> normalize(a.answer()), (a, b) -> a));
        List<PatternObservation> observations = llm.call(prompt, llm.toJson(input), 0.2,
                root -> parse(root, userTexts, dismissedNames));
        return new PatternAnalysis(prompt.version(), observations);
    }

    List<PatternObservation> parse(JsonNode root, Map<String, String> userTexts, List<String> dismissedNames) {
        JsonNode items = root.path("observations");
        if (!items.isArray() || items.size() > MAX_OBSERVATIONS) {
            throw new AgentResponseException("observations는 0~" + MAX_OBSERVATIONS + "개의 배열이어야 합니다.");
        }

        List<PatternObservation> result = new ArrayList<>();
        int droppedForNoGroundedEvidence = 0;
        for (JsonNode item : items) {
            String name = Json.text(item, "name").trim();
            String strategy = Json.text(item, "improvementStrategy").trim();
            if (name.isBlank() || name.length() > 100) {
                throw new AgentResponseException("patternName은 1~100자여야 합니다.");
            }
            if (strategy.isBlank()) {
                throw new AgentResponseException("'" + name + "': improvementStrategy(구체적이고 측정 가능한 훈련)가 필요합니다.");
            }
            if (dismissedNames.stream().anyMatch(d -> d.equalsIgnoreCase(name))) {
                continue; // 사용자가 맞지 않다고 기각한 패턴은 다시 제안하지 않는다
            }

            List<PatternEvidenceItem> evidence = groundedEvidence(item.path("evidence"), userTexts);
            if (evidence.isEmpty()) {
                droppedForNoGroundedEvidence++;
                continue;
            }
            result.add(new PatternObservation(name, cap(Json.text(item, "description").trim(), 1000), cap(strategy, 1000), evidence));
        }
        if (result.isEmpty() && droppedForNoGroundedEvidence > 0) {
            throw new AgentResponseException("근거 인용(quote)이 실제 학습자의 글에 없습니다. quote는 해당 itemId에서 학습자가 쓴 답변, 접근 설명, 코드에서 그대로 가져와야 합니다.");
        }
        return result;
    }

    /** itemId가 분석 대상이고, quote가 그 항목에서 학습자가 쓴 글에 실제로 있는 근거만 남긴다. 한 항목은 한 번만 센다. */
    private List<PatternEvidenceItem> groundedEvidence(JsonNode evidence, Map<String, String> userTexts) {
        List<PatternEvidenceItem> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode e : evidence) {
            if (list.size() >= MAX_EVIDENCE) {
                break;
            }
            String id = Json.text(e, "itemId").trim();
            String quote = normalize(Json.text(e, "quote"));
            String text = userTexts.get(id);
            if (text != null && quote.length() >= MIN_QUOTE_CHARS && text.contains(quote) && seen.add(id)) {
                list.add(new PatternEvidenceItem(id, cap(quote, 300), cap(Json.text(e, "note").trim(), 500)));
            }
        }
        return list;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static String cap(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
