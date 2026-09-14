package com.personal.ai.llm.ollama;

import com.personal.ai.core.model.AiModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfiguration {

    @Bean
    public RestClient ollamaRestClient(OllamaProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Bean
    public AiModel aiModel(RestClient ollamaRestClient, OllamaProperties properties) {
        return new OllamaAiModel(ollamaRestClient, properties);
    }
}
