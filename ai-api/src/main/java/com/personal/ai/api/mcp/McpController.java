package com.personal.ai.api.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.ai.api.service.AiService;
import com.personal.ai.core.model.AiRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MCP(Streamable HTTP) 서버. Claude Code/Desktop에서 URL로 등록해 비서를 도구로 호출한다. */
@RestController
public class McpController {

    private static final String TOOL = "ask_assistant";

    private final AiService aiService;
    private final String token;

    public McpController(AiService aiService, @Value("${mcp.token:}") String token) {
        this.aiService = aiService;
        this.token = token;
    }

    @PostMapping("/mcp")
    public ResponseEntity<Object> handle(@RequestBody JsonNode rpc,
                                         @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!token.isBlank() && !("Bearer " + token).equals(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String method = rpc.path("method").asText();
        JsonNode id = rpc.get("id");
        if (id == null) { // notification (예: notifications/initialized)
            return ResponseEntity.accepted().build();
        }

        return switch (method) {
            case "initialize" -> ok(id, Map.of(
                    "protocolVersion", rpc.path("params").path("protocolVersion").asText("2025-03-26"),
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "personal-ai", "version", "0.1.0")));
            case "ping" -> ok(id, Map.of());
            case "tools/list" -> ok(id, Map.of("tools", List.of(toolDefinition())));
            case "tools/call" -> ok(id, callTool(rpc.path("params")));
            default -> ResponseEntity.ok(Map.of("jsonrpc", "2.0", "id", id,
                    "error", Map.of("code", -32601, "message", "Method not found: " + method)));
        };
    }

    private Map<String, Object> toolDefinition() {
        return Map.of(
                "name", TOOL,
                "description", "나의 개인 AI 비서에게 질문한다. 설정된 LLM(Ollama 로컬 또는 Claude)이 답한다.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "userPrompt", Map.of("type", "string", "description", "질문 내용"),
                                "systemPrompt", Map.of("type", "string", "description", "선택: 역할/지침")),
                        "required", List.of("userPrompt")));
    }

    private Map<String, Object> callTool(JsonNode params) {
        JsonNode args = params.path("arguments");
        if (!TOOL.equals(params.path("name").asText()) || args.path("userPrompt").asText().isBlank()) {
            return toolResult("알 수 없는 도구이거나 userPrompt가 비어 있습니다.", true);
        }

        AiRequest request = new AiRequest();
        request.setUserPrompt(args.get("userPrompt").asText());
        request.setSystemPrompt(args.path("systemPrompt").asText(null));
        try {
            return toolResult(aiService.chat(request).getContent(), false);
        } catch (RuntimeException e) {
            return toolResult("LLM 호출 실패: " + e.getMessage(), true);
        }
    }

    private Map<String, Object> toolResult(String text, boolean isError) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", isError);
    }

    private ResponseEntity<Object> ok(JsonNode id, Object result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return ResponseEntity.ok(body);
    }
}
