package com.personal.ai.api.notify;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/discord")
public class NotifyController {

    private final DiscordReminderService service;

    public NotifyController(DiscordReminderService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Boolean> status() {
        return Map.of("enabled", service.enabled());
    }

    /** 설정 확인용: 오늘의 알림을 지금 한 번 보낸다. */
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test() {
        service.sendNow();
    }
}
