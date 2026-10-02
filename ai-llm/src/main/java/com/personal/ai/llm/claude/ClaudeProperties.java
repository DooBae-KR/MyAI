package com.personal.ai.llm.claude;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claude")
public class ClaudeProperties {
    private String baseUrl = "https://api.anthropic.com";
    private String apiKey = "";
    private String model = "claude-sonnet-5-5";
    private String anthropicVersion = "2023-06-01";
    private int maxTokens = 4096;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getAnthropicVersion() { return anthropicVersion; }
    public void setAnthropicVersion(String anthropicVersion) { this.anthropicVersion = anthropicVersion; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
