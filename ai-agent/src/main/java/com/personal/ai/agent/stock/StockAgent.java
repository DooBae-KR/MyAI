package com.personal.ai.agent.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.Json;
import com.personal.ai.agent.Prompt;
import com.personal.ai.agent.StructuredLlm;
import com.personal.ai.core.model.AiModel;

import java.util.Set;

/** 시세·지표 스냅샷으로 BUY/SELL/HOLD와 근거를 낸다. 시세 조회나 알림은 모른다. */
public class StockAgent {

    private static final Set<String> DECISIONS = Set.of("BUY", "SELL", "HOLD");

    private final StructuredLlm llm;
    private final Prompt prompt = Prompt.load("stock-agent");

    public StockAgent(AiModel model) {
        this.llm = new StructuredLlm(model);
    }

    public StockDecision decide(StockSnapshot snapshot) {
        return llm.call(prompt, llm.toJson(snapshot), 0.2, root -> parse(snapshot.symbol(), root));
    }

    StockDecision parse(String symbol, JsonNode root) {
        String decision = Json.text(root, "decision").trim().toUpperCase();
        if (!DECISIONS.contains(decision)) {
            throw new AgentResponseException("decision은 BUY, SELL, HOLD 중 하나여야 합니다.");
        }
        JsonNode confidence = root.path("confidence");
        if (!confidence.isNumber() || confidence.asDouble() < 0 || confidence.asDouble() > 1) {
            throw new AgentResponseException("confidence는 0~1 숫자여야 합니다.");
        }
        String reasoning = Json.text(root, "reasoning").trim();
        if (reasoning.isEmpty()) {
            throw new AgentResponseException("reasoning이 비어 있습니다.");
        }
        return new StockDecision(symbol, decision, confidence.asDouble(), reasoning, prompt.version());
    }
}
