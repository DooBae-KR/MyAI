package com.personal.ai.llm;

import org.springframework.web.client.RestClientException;

/** LLM 호출 실패. RestClientException이라 서비스의 기존 오류 매핑(503)을 그대로 탄다. */
public class LlmCallException extends RestClientException {
    public LlmCallException(String message) {
        super(message);
    }
}
