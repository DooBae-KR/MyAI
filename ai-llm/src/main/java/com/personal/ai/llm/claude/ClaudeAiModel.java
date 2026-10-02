package com.personal.ai.llm.claude;

import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClaudeAiModel implements AiModel {

    private final RestClient restClient;
    private final ClaudeProperties properties;

    public ClaudeAiModel(RestClient restClient, ClaudeProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public AiResponse chat(AiRequest request) {
        String model = request.getModel() == null || request.getModel().isBlank()
                ? properties.getModel()
                : request.getModel();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", properties.getMaxTokens());
        body.put("messages", List.of(Map.of("role", "user", "content", request.getUserPrompt())));

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("system", request.getSystemPrompt());
        }
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }

        Map<?, ?> response = restClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return new AiResponse(extractText(response));
    }

    private String extractText(Map<?, ?> response) {
        if (response == null || !(response.get("content") instanceof List<?> blocks)) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (Object block : blocks) {
            if (block instanceof Map<?, ?> map && "text".equals(map.get("type")) && map.get("text") != null) {
                text.append(map.get("text"));
            }
        }
        return text.toString();
    }
}
