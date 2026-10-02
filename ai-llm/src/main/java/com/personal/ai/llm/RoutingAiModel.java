package com.personal.ai.llm;

import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;
import com.personal.ai.core.model.LlmProvider;
import com.personal.ai.llm.claudecode.ClaudeCodeAiModel;
import com.personal.ai.llm.claudecode.ClaudeCodeDetector;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 현재 선택된 LLM으로 호출을 보낸다. 선택은 앱 실행 중에 바꿀 수 있다(설정 화면).
 * 진단·채점·커리큘럼·MCP가 모두 이 AiModel을 쓰므로 선택이 전부에 적용된다.
 */
public class RoutingAiModel implements AiModel {

    /** 모델 이름이 null이면 기본 모델을 쓴다(Claude Code는 CLI 기본 모델). */
    public record Selection(LlmProvider provider, String ollamaModel, String claudeModel, String claudeCodeModel) {}

    private final Map<LlmProvider, AiModel> models = new ConcurrentHashMap<>();
    private final ClaudeCodeDetector claudeCodeDetector; // 테스트에서는 null일 수 있다
    private final Map<LlmProvider, String> defaultModels = new EnumMap<>(LlmProvider.class);
    private final AtomicReference<Selection> selection;

    public RoutingAiModel(Map<LlmProvider, AiModel> models, LlmProvider initial,
                          String defaultOllamaModel, String defaultClaudeModel) {
        this(models, initial, defaultOllamaModel, defaultClaudeModel, null);
    }

    public RoutingAiModel(Map<LlmProvider, AiModel> models, LlmProvider initial,
                          String defaultOllamaModel, String defaultClaudeModel, ClaudeCodeDetector claudeCodeDetector) {
        if (!models.containsKey(initial)) {
            throw new IllegalArgumentException("사용할 수 없는 LLM입니다: " + initial);
        }
        this.models.putAll(models);
        this.claudeCodeDetector = claudeCodeDetector;
        this.defaultModels.put(LlmProvider.OLLAMA, defaultOllamaModel);
        this.defaultModels.put(LlmProvider.CLAUDE, defaultClaudeModel);
        this.defaultModels.put(LlmProvider.CLAUDE_CODE, null); // null이면 CLI의 기본 모델
        this.selection = new AtomicReference<>(new Selection(initial, null, null, null));
    }

    public Selection selection() {
        return selection.get();
    }

    public boolean isAvailable(LlmProvider provider) {
        return models.containsKey(provider);
    }

    /** 마지막 Claude Code 확인 결과(못 찾았다면 이유 포함). 확인한 적이 없으면 null. */
    public ClaudeCodeDetector.Detection claudeCodeDetection() {
        return claudeCodeDetector == null ? null : claudeCodeDetector.last();
    }

    /** 앱을 다시 시작하지 않고 Claude Code를 다시 찾는다. 찾으면 선택 가능해지고, 못 찾으면 선택 목록에서 빠진다. */
    public ClaudeCodeDetector.Detection redetectClaudeCode() {
        if (claudeCodeDetector == null) {
            return null;
        }
        ClaudeCodeDetector.Detection detection = claudeCodeDetector.detect();
        if (detection.found()) {
            models.put(LlmProvider.CLAUDE_CODE, claudeCodeDetector.newModel());
        } else {
            models.remove(LlmProvider.CLAUDE_CODE);
        }
        return detection;
    }

    public String defaultModel(LlmProvider provider) {
        return defaultModels.get(provider);
    }

    /** 선택한 provider에서 실제로 쓰일 모델 이름. */
    public String effectiveModel(Selection s) {
        String override = switch (s.provider()) {
            case CLAUDE -> s.claudeModel();
            case CLAUDE_CODE -> s.claudeCodeModel();
            case OLLAMA -> s.ollamaModel();
        };
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
        AiModel model = models.get(current.provider());
        if (model == null) { // 선택해 둔 LLM을 지금은 쓸 수 없는 경우(예: Claude Code를 다시 확인했더니 없어짐)
            throw new LlmCallException("선택한 LLM(" + current.provider() + ")을 지금은 사용할 수 없습니다. 설정에서 다른 LLM을 선택하세요.");
        }
        return model.chat(routed);
    }
}
