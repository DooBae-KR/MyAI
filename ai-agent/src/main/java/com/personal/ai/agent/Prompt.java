package com.personal.ai.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** classpath의 prompts/&lt;name&gt;.md. 맨 위 front matter의 promptVersion을 함께 읽는다. */
public record Prompt(String version, String text) {

    public static Prompt load(String name) {
        String path = "/prompts/" + name + ".md";
        try (InputStream in = Prompt.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("프롬프트 파일을 찾을 수 없습니다: " + path);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    static Prompt parse(String raw) {
        String version = "0";
        String body = raw;
        if (raw.startsWith("---\n")) {
            int end = raw.indexOf("\n---\n", 3);
            if (end > 0) {
                for (String line : raw.substring(4, end).split("\n")) {
                    if (line.startsWith("promptVersion:")) {
                        version = line.substring("promptVersion:".length()).trim();
                    }
                }
                body = raw.substring(end + 5);
            }
        }
        return new Prompt(version, body.trim());
    }
}
