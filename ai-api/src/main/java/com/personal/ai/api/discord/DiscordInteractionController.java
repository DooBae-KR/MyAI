package com.personal.ai.api.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

/**
 * Discord가 호출하는 Interactions 엔드포인트(POST /discord/interactions). /api 밖에 있어 APP_TOKEN이 아니라 **Discord의 서명**으로 보호한다.
 * DISCORD_PUBLIC_KEY가 없으면 엔드포인트를 숨기고(404), 서명이 틀리면 401을 돌려준다(Discord도 이 검사를 요구한다).
 */
@RestController
public class DiscordInteractionController {

    private static final Logger log = LoggerFactory.getLogger(DiscordInteractionController.class);

    private final DiscordCommandService commands;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final DiscordSignature signature;

    public DiscordInteractionController(DiscordCommandService commands, ObjectMapper mapper, Clock clock,
                                        @Value("${discord.public-key:}") String publicKey) {
        this.commands = commands;
        this.mapper = mapper;
        this.clock = clock;
        DiscordSignature parsed = null;
        if (!publicKey.isBlank()) {
            try {
                parsed = new DiscordSignature(publicKey);
            } catch (IllegalArgumentException e) {
                log.warn("DISCORD_PUBLIC_KEY 형식이 올바르지 않아 Discord 명령을 끕니다: {}", e.getMessage());
            }
        }
        this.signature = parsed;
    }

    @PostMapping(value = "/discord/interactions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> interactions(@RequestBody String body,
                                          @RequestHeader(value = "X-Signature-Ed25519", required = false) String sig,
                                          @RequestHeader(value = "X-Signature-Timestamp", required = false) String timestamp) throws Exception {
        if (signature == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!signature.verify(sig, timestamp, body, Instant.now(clock))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode interaction = mapper.readTree(body);
        return ResponseEntity.ok(commands.handle(interaction));
    }
}
