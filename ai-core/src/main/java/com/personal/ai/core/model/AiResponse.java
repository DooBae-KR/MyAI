package com.personal.ai.core.model;

public class AiResponse {
    private String content;

    public AiResponse() {}

    public AiResponse(String content) {
        this.content = content;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
