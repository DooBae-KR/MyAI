package com.personal.ai.api.controller;

import com.personal.ai.api.service.AiService;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public AiResponse chat(@RequestBody AiRequest request) {
        return aiService.chat(request);
    }
}
