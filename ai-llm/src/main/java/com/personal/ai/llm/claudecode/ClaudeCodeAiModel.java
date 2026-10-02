package com.personal.ai.llm.claudecode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;
import com.personal.ai.llm.LlmCallException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 로컬에 설치된 Claude Code CLI(claude -p)로 호출한다. API 키 대신 사용자가 CLI에 한 로그인(claude)을 쓴다.
 *
 * 채점 답변 등 사용자 입력이 프롬프트에 들어가므로 모델이 이 PC에서 아무것도 하지 못하게 막는다:
 * 도구 전부 차단, MCP 비활성, 빈 임시 폴더에서 실행, 프롬프트는 인자가 아니라 표준입력으로 전달.
 */
public class ClaudeCodeAiModel implements AiModel {

    /** 이 이름들을 막으면 모델에는 도구가 제공되지 않는다(실제 시험으로 확인). 빈 인자(--tools "")는 윈도우 cmd에서 깨질 수 있어 쓰지 않는다. */
    static final String DISALLOWED_TOOLS =
            "Bash,Edit,Write,Read,Glob,Grep,WebFetch,WebSearch,NotebookEdit,Task,Agent,Skill,TodoWrite,BashOutput,KillShell,SlashCommand";
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,99}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeCodeProperties properties;

    public ClaudeCodeAiModel(ClaudeCodeProperties properties) {
        this.properties = properties;
    }

    @Override
    public AiResponse chat(AiRequest request) {
        List<String> command = prefix(isWindows(), properties.getCommand());
        command.addAll(List.of("-p", "--output-format", "json", "--no-session-persistence", "--strict-mcp-config",
                "--permission-mode", "dontAsk", "--disallowedTools", DISALLOWED_TOOLS));
        String model = request.getModel();
        if (model != null && !model.isBlank()) {
            if (!MODEL_NAME.matcher(model.trim()).matches()) {
                throw new LlmCallException("모델 이름이 올바르지 않습니다: " + model);
            }
            command.addAll(List.of("--model", model.trim()));
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("personal-ai-claude-");
            ProcessBuilder builder = new ProcessBuilder(command).directory(workDir.toFile());
            scrub(builder.environment());
            Process process = builder.start();

            CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(buildPrompt(request).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // CLI가 입력을 읽기 전에 끝난 경우: 아래에서 종료 코드와 stderr로 원인을 보고한다
            }

            if (!process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new LlmCallException("Claude Code 응답 시간 초과(" + properties.getTimeoutSeconds() + "초)");
            }
            return parse(process.exitValue(), text(stdout), text(stderr));
        } catch (IOException e) {
            throw new LlmCallException("Claude Code CLI를 실행할 수 없습니다. 설치되어 있고 PATH에 있는지 확인하세요: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("Claude Code 호출이 중단되었습니다.");
        } finally {
            deleteQuietly(workDir);
        }
    }

    /** 윈도우에서는 npm이 만든 claude.cmd도 찾을 수 있도록 cmd /c로 실행한다. 이후 인자는 모두 ASCII 고정값이다. */
    static List<String> prefix(boolean windows, String command) {
        List<String> prefix = new ArrayList<>();
        if (windows) {
            prefix.addAll(List.of("cmd", "/c"));
        }
        prefix.add(command);
        return prefix;
    }

    /** 환경변수에 API 키가 있으면 CLI가 로그인 대신 API 키(과금)를 쓰므로 자식 프로세스에서는 지운다. */
    static void scrub(Map<String, String> environment) {
        environment.remove("ANTHROPIC_API_KEY");
        environment.remove("ANTHROPIC_AUTH_TOKEN");
    }

    static String buildPrompt(AiRequest request) {
        if (request.getSystemPrompt() == null || request.getSystemPrompt().isBlank()) {
            return request.getUserPrompt();
        }
        return "[SYSTEM]\n" + request.getSystemPrompt() + "\n\n[USER]\n" + request.getUserPrompt();
    }

    static AiResponse parse(int exitCode, String stdout, String stderr) {
        JsonNode json = null;
        try {
            json = MAPPER.readTree(stdout);
        } catch (JsonProcessingException ignored) {
            // JSON이 아니면 아래에서 stderr/stdout 앞부분으로 오류를 만든다
        }
        if (json != null && json.has("result") && !json.path("is_error").asBoolean(false) && exitCode == 0) {
            return new AiResponse(json.path("result").asText(""));
        }
        String reason = json != null && json.hasNonNull("result") ? json.path("result").asText() : firstNonBlank(stderr, stdout);
        throw new LlmCallException("Claude Code 호출 실패(종료 코드 " + exitCode + "): " + abbreviate(reason) + loginHint(reason, json));
    }

    private static String loginHint(String reason, JsonNode json) {
        String text = reason == null ? "" : reason.toLowerCase();
        boolean authProblem = text.contains("login") || text.contains("not logged in") || text.contains("invalid api key")
                || text.contains("authenticat") || (json != null && json.path("api_error_status").asInt(0) == 401);
        return authProblem ? " — 터미널에서 claude 를 실행해 로그인한 뒤 다시 시도하세요." : "";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    private static String abbreviate(String text) {
        String t = text == null ? "" : text.strip();
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static byte[] readAll(InputStream in) {
        try (in) {
            return in.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static String text(CompletableFuture<byte[]> future) {
        try {
            return new String(future.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // 임시 폴더 정리 실패는 무시한다
        }
    }
}
