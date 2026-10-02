package com.personal.ai.llm;

import com.personal.ai.core.model.LlmProvider;
import com.personal.ai.llm.claude.ClaudeProperties;
import com.personal.ai.llm.claudecode.ClaudeCodeProperties;
import com.personal.ai.llm.ollama.OllamaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmConfigurationTest {

    private RoutingAiModel build(String apiKey, String provider) {
        ClaudeProperties claude = new ClaudeProperties();
        claude.setApiKey(apiKey);
        ClaudeCodeProperties claudeCode = new ClaudeCodeProperties();
        claudeCode.setCommand("personal-ai-test-no-such-cli"); // 이 PC에 있는 진짜 claude의 영향을 받지 않게 한다
        claudeCode.setSearchCommonLocations(false);
        return new LlmConfiguration().aiModel(new OllamaProperties(), claude, claudeCode, provider);
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

    @Test
    void claudeCodeIsAvailableOnlyWhenTheCliExists() {
        assertFalse(build("", "ollama").isAvailable(LlmProvider.CLAUDE_CODE));
        assertThrows(IllegalStateException.class, () -> build("", "claude-code"));
    }

    @Test
    void autoPrefersClaudeOverLocalOllamaWhenAvailable() {
        // 키도 CLI도 없으면 Ollama, 키가 있으면 Claude API (Claude Code는 별도 테스트에서 가짜 CLI로 확인)
        assertEquals(LlmProvider.OLLAMA, build("", "auto").selection().provider());
        assertEquals(LlmProvider.CLAUDE, build("sk-ant-abc", "auto").selection().provider());
        assertEquals(LlmProvider.CLAUDE, build("sk-ant-abc", " AUTO ").selection().provider());
    }
}
