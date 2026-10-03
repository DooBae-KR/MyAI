package com.personal.ai.api.notion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NotionClientTest {

    private static final String TOKEN = "secret_abcdefghijklmnopqrstuvwxyz";
    private final ObjectMapper mapper = new ObjectMapper();

    private record Captured(String path, String auth, String version, String body) {}

    private HttpServer server(int status, String responseBody, AtomicReference<Captured> captured) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/", ex -> {
            captured.set(new Captured(ex.getRequestURI().getPath(), ex.getRequestHeaders().getFirst("Authorization"),
                    ex.getRequestHeaders().getFirst("Notion-Version"), new String(ex.getRequestBody().readAllBytes())));
            byte[] out = responseBody.getBytes();
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        s.start();
        return s;
    }

    private NotionClient client(HttpServer s) {
        return new NotionClient(mapper, TOKEN, "http://127.0.0.1:" + s.getAddress().getPort());
    }

    @Test
    void createsAPageInTheDatabaseWithTitlePropertyAndBlocksAndReturnsItsUrl() throws Exception {
        var captured = new AtomicReference<Captured>();
        HttpServer s = server(200, "{\"url\":\"https://www.notion.so/page-123\"}", captured);
        try {
            String url = client(s).createPage("0123456789abcdef0123456789abcdef", "이름", "학습 로그 2026-10-03",
                    List.of(NotionClient.Block.heading("오늘의 학습"), NotionClient.Block.bullet("□ Vue Step 4"), NotionClient.Block.paragraph("끝")));

            assertEquals("https://www.notion.so/page-123", url);
            Captured c = captured.get();
            assertEquals("/v1/pages", c.path());
            assertEquals("Bearer " + TOKEN, c.auth());
            assertEquals(NotionClient.VERSION, c.version());
            JsonNode body = mapper.readTree(c.body());
            assertEquals("0123456789abcdef0123456789abcdef", body.at("/parent/database_id").asText());
            assertEquals("학습 로그 2026-10-03", body.at("/properties/이름/title/0/text/content").asText());
            assertEquals(3, body.get("children").size());
            assertEquals("heading_2", body.at("/children/0/type").asText());
            assertEquals("오늘의 학습", body.at("/children/0/heading_2/rich_text/0/text/content").asText());
            assertEquals("□ Vue Step 4", body.at("/children/1/bulleted_list_item/rich_text/0/text/content").asText());
            assertEquals("paragraph", body.at("/children/2/type").asText());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void truncatesLongTextAndCapsBlocksToNotionsLimits() throws Exception {
        var captured = new AtomicReference<Captured>();
        HttpServer s = server(200, "{}", captured);
        try {
            List<NotionClient.Block> many = new ArrayList<>();
            many.add(NotionClient.Block.paragraph("가".repeat(5000)));
            for (int i = 0; i < 150; i++) many.add(NotionClient.Block.bullet("항목" + i));

            assertNull(client(s).createPage("0123456789abcdef0123456789abcdef", "Name", "t", many)); // url 없으면 null

            JsonNode body = mapper.readTree(captured.get().body());
            assertEquals(NotionClient.MAX_BLOCKS, body.get("children").size());
            assertEquals(NotionClient.MAX_TEXT, body.at("/children/0/paragraph/rich_text/0/text/content").asText().length());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void errorMessageHasStatusAndNotionsMessageButNeverTheToken() throws Exception {
        var captured = new AtomicReference<Captured>();
        HttpServer s = server(400, "{\"message\":\"Name is not a property that exists.\"}", captured);
        try {
            var e = assertThrows(IOException.class, () -> client(s).createPage("0123456789abcdef0123456789abcdef", "Name", "t", List.of()));
            assertTrue(e.getMessage().contains("HTTP 400"));
            assertTrue(e.getMessage().contains("Name is not a property that exists."));
            assertFalse(e.getMessage().contains(TOKEN));
        } finally {
            s.stop(0);
        }
    }
}
