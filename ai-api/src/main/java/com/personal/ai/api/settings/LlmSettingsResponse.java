package com.personal.ai.api.settings;

/** ollamaModel/claudeModel은 사용자가 지정한 값(없으면 null = 기본 모델). */
public record LlmSettingsResponse(String provider, String ollamaModel, String claudeModel, String activeModel,
                                  boolean claudeAvailable, Defaults defaults) {

    public record Defaults(String ollamaModel, String claudeModel) {}
}
