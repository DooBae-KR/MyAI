package com.personal.ai.api.service;

import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private final AiModel aiModel;

    public AiService(AiModel aiModel) {
        this.aiModel = aiModel;
    }

    public AiResponse chat(AiRequest request) {
        return aiModel.chat(request);
    }
}
