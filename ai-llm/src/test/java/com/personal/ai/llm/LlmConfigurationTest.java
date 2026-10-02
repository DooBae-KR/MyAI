package com.personal.ai.llm;

import com.personal.ai.core.model.LlmProvider;
import com.personal.ai.llm.claude.ClaudeProperties;
import com.personal.ai.llm.ollama.OllamaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmConfigurationTest {

    private RoutingAiModel build(String apiKey, String provider) {
        ClaudeProperties claude = new ClaudeProperties();
        claude.setApiKey(apiKey);
        return new LlmConfiguration().aiModel(new OllamaProperties(), claude, provider);
    }

    @Test
    void claudeIsAvailableOnlyWithAUsableKey() {
        assertTrue(build("sk-ant-api03-abc_123", "ollama").isAvailable(LlmProvider.CLAUDE));
        assertFalse(build("", "ollama").isAvailable(LlmProvider.CLAUDE));
        assertFalse(build(null, "ollama").isAvailable(LlmProvider.CLAUDE));
        assertTrue(build("", "ollama").isAvailable(LlmProvider.OLLAMA));
    }

    @Test
    void placeholderKeysAreTreatedAsMissing() {
        assertFalse(build("여기에_API_키를_입력하세요", "ollama").isAvailable(LlmProvider.CLAUDE)); // 비ASCII
        assertFalse(build("your key here", "ollama").isAvailable(LlmProvider.CLAUDE)); // 공백
        assertFalse(build("  ", "ollama").isAvailable(LlmProvider.CLAUDE));
    }

    @Test
    void startingWithClaudeButNoUsableKeyFailsFast() {
        assertThrows(IllegalStateException.class, () -> build("여기에_API_키를_입력하세요", "claude"));
        assertEquals(LlmProvider.CLAUDE, build("sk-ant-abc", "Claude").selection().provider());
        assertEquals(LlmProvider.OLLAMA, build("", " OLLAMA ").selection().provider());
    }
}
