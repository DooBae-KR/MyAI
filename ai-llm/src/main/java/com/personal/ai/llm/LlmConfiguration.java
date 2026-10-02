package com.personal.ai.llm;

import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.LlmProvider;
import com.personal.ai.llm.claude.ClaudeAiModel;
import com.personal.ai.llm.ollama.OllamaAiModel;
import com.personal.ai.llm.claude.ClaudeProperties;
import com.personal.ai.llm.claudecode.ClaudeCodeAiModel;
import com.personal.ai.llm.claudecode.ClaudeCodeProperties;

import com.personal.ai.llm.ollama.OllamaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ollama는 항상, Claude는 API 키가 있을 때만 준비한다. ai.provider(AI_PROVIDER)는 시작할 때의 기본 선택이고,
 * 이후에는 설정 화면의 선택(DB 저장)이 이를 덮어쓴다.
 */
@Configuration
@EnableConfigurationProperties({OllamaProperties.class, ClaudeProperties.class, ClaudeCodeProperties.class})
public class LlmConfiguration {

    @Bean
    public RoutingAiModel aiModel(OllamaProperties ollama, ClaudeProperties claude, ClaudeCodeProperties claudeCode,
                                  @Value("${ai.provider:ollama}") String provider) {
        Map<LlmProvider, AiModel> models = new EnumMap<>(LlmProvider.class);
        models.put(LlmProvider.OLLAMA, new OllamaAiModel(
                RestClient.builder().baseUrl(ollama.getBaseUrl()).build(), ollama));
        if (isUsableKey(claude.getApiKey())) {
            models.put(LlmProvider.CLAUDE, new ClaudeAiModel(RestClient.builder()
                    .baseUrl(claude.getBaseUrl())
                    .defaultHeader("x-api-key", claude.getApiKey())
                    .defaultHeader("anthropic-version", claude.getAnthropicVersion())
                    .build(), claude));
        }

        if (ClaudeCodeAiModel.isInstalled(claudeCode)) {
            models.put(LlmProvider.CLAUDE_CODE, new ClaudeCodeAiModel(claudeCode));
        }

        LlmProvider initial = LlmProvider.valueOf(provider.trim().toUpperCase().replace('-', '_'));
        if (!models.containsKey(initial)) {
            throw new IllegalStateException(initial == LlmProvider.CLAUDE_CODE
                    ? "ai.provider=claude-code 이지만 Claude Code CLI(claude)를 찾을 수 없습니다."
                    : "ai.provider=claude 이지만 ANTHROPIC_API_KEY(claude.api-key)가 설정되지 않았습니다.");
        }
        return new RoutingAiModel(models, initial, ollama.getModel(), claude.getModel());
    }

    /**
     * API 키는 공백 없는 ASCII 문자열이다. 비었거나 자리표시자(예: "여기에_키를_입력하세요")가 그대로 남은 경우를
     * 진짜 키로 취급하면 Claude가 "사용 가능"으로 보이고 호출 때마다 실패하므로 "키 없음"으로 본다.
     */
    static boolean isUsableKey(String key) {
        return key != null && key.matches("[\\x21-\\x7E]+");
    }
}
