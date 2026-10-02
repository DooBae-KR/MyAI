package com.personal.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;

import java.util.function.Function;

/**
 * LLM에 JSON 응답을 요구하는 호출의 공통 처리: JSON 추출 → 검증(parser) → 실패 시 사유를 덧붙여 1회 재시도.
 * Agent는 프롬프트와 검증 규칙만 가지면 된다.
 */
public final class StructuredLlm {

    private static final int ATTEMPTS = 2;

    private final AiModel model;
    private final ObjectMapper mapper = new ObjectMapper();

    public StructuredLlm(AiModel model) {
        this.model = model;
    }

    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** parser는 형식 위반 시 AgentResponseException을 던진다. */
    public <T> T call(Prompt prompt, String userPrompt, double temperature, Function<JsonNode, T> parser) {
        String retryNote = "";
        AgentResponseException last = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            AiRequest request = new AiRequest();
            request.setSystemPrompt(prompt.text());
            request.setUserPrompt(userPrompt + retryNote);
            request.setTemperature(temperature);
            try {
                return parser.apply(extractObject(model.chat(request).getContent()));
            } catch (AgentResponseException e) {
                last = e;
                retryNote = "\n\n[이전 응답 오류] " + e.getMessage() + "\n규칙에 맞는 JSON만 다시 출력하라.";
            }
        }
        throw last;
    }

    /** 추론 모델의 &lt;think&gt; 블록과 ``` 코드펜스를 걷어내고 첫 '{' ~ 마지막 '}'만 파싱한다. */
    JsonNode extractObject(String content) {
        if (content == null || content.isBlank()) {
            throw new AgentResponseException("응답이 비어 있습니다.");
        }
        String text = content.replaceAll("(?s)<think>.*?</think>", "");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AgentResponseException("JSON 객체를 찾을 수 없습니다.");
        }
        try {
            return mapper.readTree(text.substring(start, end + 1));
        } catch (JsonProcessingException e) {
            throw new AgentResponseException("JSON 파싱 실패: " + e.getOriginalMessage());
        }
    }
}
