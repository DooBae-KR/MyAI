package com.personal.ai.api.learning;

import com.personal.ai.agent.AgentResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

/** Agent 호출 실패를 HTTP 상태로 옮긴다: 응답 형식 위반 502, LLM 연결 실패 503. */
public final class LlmCalls {

    private LlmCalls() {}

    public static <T> T run(Supplier<T> call) {
        try {
            return call.get();
        } catch (AgentResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LLM 응답 처리 실패: " + e.getMessage());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LLM 호출 실패: " + e.getMessage());
        }
    }
}
