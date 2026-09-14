package com.personal.ai.llm.ollama;

import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

public class OllamaAiModel implements AiModel {

    private final RestClient restClient;
    private final OllamaProperties properties;

    public OllamaAiModel(RestClient restClient, OllamaProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public AiResponse chat(AiRequest request) {
        String model = request.getModel() == null || request.getModel().isBlank()
                ? properties.getModel()
                : request.getModel();

        String prompt = buildPrompt(request);

        Map<?, ?> response = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", model,
                        "prompt", prompt,
                        "stream", false
                ))
                .retrieve()
                .body(Map.class);

        String content = "";

        if (response != null) {
            Object value = response.get("response");
            if (value != null) {
                content = value.toString();
            }
        }
        return new AiResponse(content);
    }

    private String buildPrompt(AiRequest request) {
        if (request.getSystemPrompt() == null || request.getSystemPrompt().isBlank()) {
            return request.getUserPrompt();
        }

        return "[SYSTEM]\n" + request.getSystemPrompt()
                + "\n\n[USER]\n" + request.getUserPrompt();
    }
}
