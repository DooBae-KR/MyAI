package com.personal.ai.llm.claudecode;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claude-code")
public class ClaudeCodeProperties {
    /** Claude Code CLI 실행 명령. 보통 PATH의 claude. */
    private String command = "claude";
    private int timeoutSeconds = 180;

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
