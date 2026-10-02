package com.personal.ai.llm;

import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;
import com.personal.ai.core.model.LlmProvider;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 현재 선택된 LLM으로 호출을 보낸다. 선택은 앱 실행 중에 바꿀 수 있다(설정 화면).
 * 진단·채점·커리큘럼·MCP가 모두 이 AiModel을 쓰므로 선택이 전부에 적용된다.
 */
public class RoutingAiModel implements AiModel {

    /** 모델 이름이 null이면 기본 모델을 쓴다. */
    public record Selection(LlmProvider provider, String ollamaModel, String claudeModel) {}

    private final Map<LlmProvider, AiModel> models;
    private final Map<LlmProvider, String> defaultModels = new EnumMap<>(LlmProvider.class);
    private final AtomicReference<Selection> selection;

    public RoutingAiModel(Map<LlmProvider, AiModel> models, LlmProvider initial,
                          String defaultOllamaModel, String defaultClaudeModel) {
        if (!models.containsKey(initial)) {
            throw new IllegalArgumentException("사용할 수 없는 LLM입니다: " + initial);
        }
        this.models = new EnumMap<>(models);
        this.defaultModels.put(LlmProvider.OLLAMA, defaultOllamaModel);
        this.defaultModels.put(LlmProvider.CLAUDE, defaultClaudeModel);
        this.selection = new AtomicReference<>(new Selection(initial, null, null));
    }

    public Selection selection() {
        return selection.get();
    }

    public boolean isAvailable(LlmProvider provider) {
        return models.containsKey(provider);
    }

    public String defaultModel(LlmProvider provider) {
        return defaultModels.get(provider);
    }

    /** 선택한 provider에서 실제로 쓰일 모델 이름. */
    public String effectiveModel(Selection s) {
        String override = s.provider() == LlmProvider.CLAUDE ? s.claudeModel() : s.ollamaModel();
        return override != null ? override : defaultModels.get(s.provider());
    }

    public void select(Selection next) {
        if (!isAvailable(next.provider())) {
            throw new IllegalArgumentException("사용할 수 없는 LLM입니다: " + next.provider());
        }
        selection.set(next);
    }

    @Override
    public AiResponse chat(AiRequest request) {
        Selection current = selection.get();
        AiRequest routed = new AiRequest(); // 호출자의 요청을 바꾸지 않도록 복사한다
        routed.setSystemPrompt(request.getSystemPrompt());
        routed.setUserPrompt(request.getUserPrompt());
        routed.setTemperature(request.getTemperature());
        routed.setModel(request.getModel() == null || request.getModel().isBlank()
                ? effectiveModel(current) : request.getModel());
        return models.get(current.provider()).chat(routed);
    }
}
