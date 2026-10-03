package com.personal.ai.api.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notion API로 데이터베이스에 페이지 하나를 만든다. 토큰은 비밀이라 로그와 예외 메시지에 넣지 않는다.
 * Notion의 제한: 한 텍스트는 2000자, 한 요청의 블록은 100개까지.
 */
public class NotionClient {

    static final String DEFAULT_BASE_URL = "https://api.notion.com";
    static final String VERSION = "2022-06-28";
    static final int MAX_TEXT = 2000;
    static final int MAX_BLOCKS = 100;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String token;

    public NotionClient(ObjectMapper mapper, String token) {
        this(mapper, token, DEFAULT_BASE_URL);
    }

    NotionClient(ObjectMapper mapper, String token, String baseUrl) {
        this.mapper = mapper;
        this.token = token;
        this.baseUrl = baseUrl;
    }

    /** 페이지 본문 블록. type은 heading_2, bulleted_list_item, paragraph. */
    public record Block(String type, String text) {
        public static Block heading(String text) { return new Block("heading_2", text); }
        public static Block bullet(String text) { return new Block("bulleted_list_item", text); }
        public static Block paragraph(String text) { return new Block("paragraph", text); }
    }

    /** @return 만들어진 페이지의 URL(Notion이 알려 주는 값, 없으면 null) */
    public String createPage(String databaseId, String titleProperty, String title, List<Block> blocks)
            throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parent", Map.of("database_id", databaseId));
        body.put("properties", Map.of(titleProperty, Map.of("title", List.of(richText(title)))));
        List<Object> children = new ArrayList<>();
        for (Block b : blocks.subList(0, Math.min(MAX_BLOCKS, blocks.size()))) {
            children.add(Map.of("object", "block", "type", b.type(), b.type(), Map.of("rich_text", List.of(richText(b.text())))));
        }
        body.put("children", children);

        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/pages"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token)
                        .header("Notion-Version", VERSION)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Notion이 거절했습니다 (HTTP " + res.statusCode() + errorMessage(res.body()) + ")");
        }
        JsonNode json = mapper.readTree(res.body());
        return json.hasNonNull("url") ? json.get("url").asText() : null;
    }

    private static Map<String, Object> richText(String text) {
        String content = text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT - 1) + "…";
        return Map.of("type", "text", "text", Map.of("content", content));
    }

    /** Notion 오류 본문의 message만 짧게 덧붙인다(어느 속성이 틀렸는지 알려 주기 때문). 본문 전체나 토큰은 넣지 않는다. */
    private String errorMessage(String body) {
        try {
            String message = mapper.readTree(body).path("message").asText("");
            return message.isBlank() ? "" : ": " + (message.length() > 200 ? message.substring(0, 200) : message);
        } catch (IOException e) {
            return "";
        }
    }
}
