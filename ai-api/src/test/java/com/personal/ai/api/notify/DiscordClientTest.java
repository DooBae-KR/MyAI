package com.personal.ai.api.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DiscordClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer server(int status, AtomicReference<String> body) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes()));
            ex.sendResponseHeaders(status, -1);
            ex.close();
        });
        s.start();
        return s;
    }

    @Test
    void sendsContentWithMentionsDisabledAndTruncatesLongText() throws Exception {
        var body = new AtomicReference<String>();
        HttpServer s = server(204, body);
        try {
            new DiscordClient(mapper, "http://127.0.0.1:" + s.getAddress().getPort() + "/hook")
                    .send("@everyone " + "가".repeat(3000));
            var json = mapper.readTree(body.get());
            assertEquals(DiscordClient.MAX_LENGTH, json.get("content").asText().length());
            assertTrue(json.get("allowed_mentions").get("parse").isEmpty());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void failureDoesNotLeakTheWebhookUrl() throws Exception {
        HttpServer s = server(401, new AtomicReference<>());
        try {
            String url = "http://127.0.0.1:" + s.getAddress().getPort() + "/secret-token";
            var e = assertThrows(IOException.class, () -> new DiscordClient(mapper, url).send("hi"));
            assertTrue(e.getMessage().contains("401"));
            assertTrue(!e.getMessage().contains("secret-token"));
        } finally {
            s.stop(0);
        }
    }

    @Test
    void onlyDiscordWebhookUrlsAreAccepted() {
        assertTrue(DiscordReminderService.isDiscordWebhook("https://discord.com/api/webhooks/1/abc"));
        assertTrue(!DiscordReminderService.isDiscordWebhook("https://evil.example/api/webhooks/1/abc"));
        assertTrue(!DiscordReminderService.isDiscordWebhook("http://discord.com/api/webhooks/1/abc"));
    }
}
