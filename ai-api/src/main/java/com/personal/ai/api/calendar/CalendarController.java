package com.personal.ai.api.calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 캘린더 앱이 구독하는 ICS 피드. 캘린더 앱은 Authorization 헤더를 보낼 수 없어서 주소 자체에 비밀 토큰을 넣는다(Discord 웹후크 주소와 같은 성격).
 * CALENDAR_TOKEN이 없거나 너무 짧으면 피드를 끄고, 토큰이 틀리면 존재를 숨기려고 404를 돌려준다.
 */
@RestController
public class CalendarController {

    private static final Logger log = LoggerFactory.getLogger(CalendarController.class);
    static final int MIN_TOKEN_LENGTH = 16;

    private final CalendarService service;
    private final byte[] token;

    public CalendarController(CalendarService service, @Value("${calendar.token:}") String token) {
        this.service = service;
        if (token.isBlank()) {
            this.token = null;
        } else if (token.length() < MIN_TOKEN_LENGTH) {
            log.warn("CALENDAR_TOKEN이 {}자보다 짧아 캘린더 피드를 끕니다.", MIN_TOKEN_LENGTH);
            this.token = null;
        } else {
            this.token = token.getBytes(StandardCharsets.UTF_8);
        }
    }

    @GetMapping(value = "/calendar/{token}/personal-ai.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        if (this.token == null || !MessageDigest.isEqual(this.token, token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .cacheControl(CacheControl.noStore())
                .body(service.ics());
    }
}
