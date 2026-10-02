package com.personal.ai.llm.claudecode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Claude Code CLI를 찾는다. 설정한 명령(기본 claude)이 실행되지 않으면 흔한 설치 위치도 확인한다.
 * IDE에서 실행한 앱은 터미널과 PATH가 달라 claude를 못 찾는 경우가 많기 때문이다.
 * 찾은 명령은 properties에 반영해 이후 호출에 쓰고, 못 찾으면 시도한 위치와 이유를 남긴다.
 */
public class ClaudeCodeDetector {

    /** command가 null이면 찾지 못한 것이고 problem에 이유가 있다. */
    public record Detection(String command, String version, String problem) {
        public boolean found() { return command != null; }
    }

    private final ClaudeCodeProperties properties;
    private volatile Detection last = new Detection(null, null, "아직 확인하지 않았습니다.");

    public ClaudeCodeDetector(ClaudeCodeProperties properties) {
        this.properties = properties;
    }

    /** 찾은 명령으로 모델을 만든다. detect()가 성공한 뒤에 호출한다. */
    public ClaudeCodeAiModel newModel() {
        return new ClaudeCodeAiModel(properties);
    }

    public Detection last() {
        return last;
    }

    public synchronized Detection detect() {
        List<String> problems = new ArrayList<>();
        for (String candidate : candidates()) {
            Probe probe = probe(candidate);
            if (probe.ok()) {
                properties.setCommand(candidate);
                return last = new Detection(candidate, probe.firstLine(), null);
            }
            problems.add(candidate + " → " + probe.firstLine());
        }
        return last = new Detection(null, null, "Claude Code(claude)를 실행할 수 없습니다. 설치되어 있는지, 터미널에서 claude --version 이 되는지 확인하세요. 시도한 위치: "
                + String.join(" / ", problems));
    }

    /** 설정한 명령이 먼저이고, 그다음 흔한 설치 위치 중 실제로 파일이 있는 것. */
    List<String> candidates() {
        Set<String> list = new LinkedHashSet<>();
        list.add(properties.getCommand());
        if (properties.isSearchCommonLocations()) {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            String home = System.getProperty("user.home", "");
            List<String> common = windows
                    ? List.of(env("APPDATA") + "\\npm\\claude.cmd", home + "\\.local\\bin\\claude.exe",
                            env("LOCALAPPDATA") + "\\Programs\\claude\\claude.exe")
                    : List.of(home + "/.local/bin/claude", home + "/.claude/local/claude",
                            "/usr/local/bin/claude", "/opt/homebrew/bin/claude");
            for (String path : common) {
                if (!path.startsWith("\\") && !path.startsWith("null") && Files.isRegularFile(Path.of(path))) {
                    list.add(path);
                }
            }
        }
        return new ArrayList<>(list);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "null" : value;
    }

    record Probe(boolean ok, String firstLine) {}

    private static Probe probe(String command) {
        try {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            List<String> cmd = ClaudeCodeAiModel.prefix(windows, command);
            cmd.add("--version");
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> read(p.getInputStream()));
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Probe(false, "응답 없음(15초)");
            }
            String line = firstLine(out.get(3, TimeUnit.SECONDS));
            return p.exitValue() == 0 ? new Probe(true, line) : new Probe(false, "종료 코드 " + p.exitValue() + (line.isEmpty() ? "" : " " + line));
        } catch (IOException e) {
            return new Probe(false, "실행 불가(" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Probe(false, "중단됨");
        } catch (Exception e) {
            return new Probe(false, "확인 실패(" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String read(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String firstLine(String text) {
        String t = text == null ? "" : text.strip();
        int nl = t.indexOf('\n');
        t = nl < 0 ? t : t.substring(0, nl).strip();
        return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }
}
