package com.personal.ai.api.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Discord Incoming Webhook으로 메시지를 보낸다. URL 자체가 비밀이므로 로그와 예외 메시지에 넣지 않는다. */
public class DiscordClient {

    static final int MAX_LENGTH = 2000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final URI webhook;

    public DiscordClient(ObjectMapper mapper, String webhookUrl) {
        this.mapper = mapper;
        this.webhook = URI.create(webhookUrl);
    }

    public void send(String text) throws IOException, InterruptedException {
        String content = text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 1) + "…";
        // allowed_mentions.parse=[] : 본문에 @everyone 등이 섞여도 아무도 호출하지 않는다.
        String body = mapper.writeValueAsString(Map.of("content", content, "allowed_mentions", Map.of("parse", new String[0])));
        HttpResponse<Void> res = http.send(HttpRequest.newBuilder(webhook)
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.discarding());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Discord가 거절했습니다 (HTTP " + res.statusCode() + ")");
        }
    }
}
