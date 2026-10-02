package com.personal.ai.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 서비스가 일부러 던진 오류(400/404/409/502/503)의 안내 문구만 {"message"}로 내려 준다.
 * 예상 못 한 예외는 여기서 다루지 않아 상세 메시지(SQL 등)가 응답에 새지 않는다(server.error.include-message=never).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "" : e.getReason()));
    }
}
