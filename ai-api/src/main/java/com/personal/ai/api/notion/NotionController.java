package com.personal.ai.api.notion;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/notion")
public class NotionController {

    private final NotionLogService service;

    public NotionController(NotionLogService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Boolean> status() {
        return Map.of("enabled", service.enabled());
    }

    /** 설정 확인용: 오늘의 학습 로그 페이지를 지금 새로 만든다(자동 기록과 달리 중복을 막지 않는다). */
    @PostMapping("/test")
    public Map<String, String> test() {
        Map<String, String> body = new HashMap<>();
        body.put("url", service.logNow());
        return body;
    }
}
