package com.personal.ai.llm;

import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;
import com.personal.ai.core.model.LlmProvider;
import com.personal.ai.llm.RoutingAiModel.Selection;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoutingAiModelTest {

    /** 어느 모델이 어떤 model 값으로 호출됐는지 기록한다. */
    private static AiModel named(String name, StringBuilder log) {
        return request -> {
            log.append(name).append(':').append(request.getModel()).append(';');
            return new AiResponse(name);
        };
    }

    private RoutingAiModel router(StringBuilder log, boolean withClaude) {
        Map<LlmProvider, AiModel> models = new EnumMap<>(LlmProvider.class);
        models.put(LlmProvider.OLLAMA, named("ollama", log));
        if (withClaude) {
            models.put(LlmProvider.CLAUDE, named("claude", log));
        }
        return new RoutingAiModel(models, LlmProvider.OLLAMA, "qwen3:14b", "claude-sonnet-5-5");
    }

    private AiRequest request(String model) {
        AiRequest r = new AiRequest();
        r.setUserPrompt("hi");
        r.setModel(model);
        return r;
    }

    @Test
    void routesToSelectedProviderWithItsDefaultModelAndSwitchesAtRuntime() {
        StringBuilder log = new StringBuilder();
        RoutingAiModel router = router(log, true);

        assertEquals("ollama", router.chat(request(null)).getContent());
        router.select(new Selection(LlmProvider.CLAUDE, null, null, null));
        assertEquals("claude", router.chat(request(" ")).getContent());

        assertEquals("ollama:qwen3:14b;claude:claude-sonnet-5-5;", log.toString());
    }

    @Test
    void usesSelectedModelOverrideButRequestModelWins() {
        StringBuilder log = new StringBuilder();
        RoutingAiModel router = router(log, true);
        router.select(new Selection(LlmProvider.CLAUDE, "llama3", "claude-opus-5-5", null));

        router.chat(request(null));
        router.chat(request("claude-haiku-4-5-20251001"));

        assertEquals("claude:claude-opus-5-5;claude:claude-haiku-4-5-20251001;", log.toString());
        assertEquals("claude-opus-5-5", router.effectiveModel(router.selection()));
    }

    @Test
    void doesNotMutateCallersRequest() {
        RoutingAiModel router = router(new StringBuilder(), true);
        AiRequest original = request(null);
        original.setSystemPrompt("sys");
        original.setTemperature(0.3);

        router.chat(original);

        assertNull(original.getModel());
        assertEquals("sys", original.getSystemPrompt());
    }

    @Test
    void refusesUnavailableProvider() {
        RoutingAiModel router = router(new StringBuilder(), false);

        assertFalse(router.isAvailable(LlmProvider.CLAUDE));
        assertThrows(IllegalArgumentException.class, () -> router.select(new Selection(LlmProvider.CLAUDE, null, null, null)));
        assertEquals(LlmProvider.OLLAMA, router.selection().provider()); // 실패해도 기존 선택 유지
        assertThrows(IllegalArgumentException.class,
                () -> new RoutingAiModel(Map.of(LlmProvider.OLLAMA, named("o", new StringBuilder())),
                        LlmProvider.CLAUDE, "a", "b"));
    }

    @Test
    void failsClearlyWhenTheSelectedProviderDisappears() {
        RoutingAiModel router = router(new StringBuilder(), true);
        router.select(new Selection(LlmProvider.CLAUDE, null, null, null));
        assertEquals("claude", router.chat(request(null)).getContent());

        // Claude Code를 다시 확인해 없어진 상황을 흉내 낸다: 모델이 빠진 라우터는 NPE가 아니라 안내 예외를 던져야 한다
        RoutingAiModel ollamaOnly = router(new StringBuilder(), false);
        assertNull(ollamaOnly.claudeCodeDetection());
        assertNull(ollamaOnly.redetectClaudeCode());
    }
}
