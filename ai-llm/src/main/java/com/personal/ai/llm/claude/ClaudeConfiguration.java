package com.personal.ai.llm.claude;

import com.personal.ai.core.model.AiModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "ai.provider", havingValue = "claude")
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfiguration {

    @Bean
    public RestClient claudeRestClient(ClaudeProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "ai.provider=claude 이지만 ANTHROPIC_API_KEY(claude.api-key)가 설정되지 않았습니다.");
        }
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-api-key", properties.getApiKey())
                .defaultHeader("anthropic-version", properties.getAnthropicVersion())
                .build();
    }

    @Bean
    public AiModel aiModel(RestClient claudeRestClient, ClaudeProperties properties) {
        return new ClaudeAiModel(claudeRestClient, properties);
    }
}
