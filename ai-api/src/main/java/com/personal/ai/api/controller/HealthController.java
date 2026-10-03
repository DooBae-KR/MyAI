package com.personal.ai.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배포 플랫폼의 상태 확인(health check)용. 인증도 DB도 쓰지 않아 앱이 떠 있는지만 알려 준다. */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public String healthz() {
        return "ok";
    }
}
