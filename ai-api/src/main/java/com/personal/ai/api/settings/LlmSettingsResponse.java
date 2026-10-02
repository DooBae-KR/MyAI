package com.personal.ai.api.settings;

/** *Model은 사용자가 지정한 값(없으면 null = 기본 모델). Claude Code의 기본은 CLI가 정한 모델이라 defaults에 이름이 없다. */
public record LlmSettingsResponse(String provider, String ollamaModel, String claudeModel, String claudeCodeModel,
                                  String activeModel, boolean claudeAvailable, boolean claudeCodeAvailable,
                                  Defaults defaults) {

    public record Defaults(String ollamaModel, String claudeModel) {}
}
