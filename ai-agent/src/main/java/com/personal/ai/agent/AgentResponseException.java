package com.personal.ai.agent;

/** LLM 응답이 약속한 형식을 지키지 못했을 때. */
public class AgentResponseException extends RuntimeException {
    public AgentResponseException(String message) {
        super(message);
    }
}
