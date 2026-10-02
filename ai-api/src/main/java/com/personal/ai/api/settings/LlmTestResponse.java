package com.personal.ai.api.settings;

public record LlmTestResponse(boolean ok, String provider, String model, long elapsedMs, String reply, String message) {
}
