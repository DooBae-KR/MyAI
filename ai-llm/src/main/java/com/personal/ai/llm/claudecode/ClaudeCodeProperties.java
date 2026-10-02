package com.personal.ai.llm.claudecode;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claude-code")
public class ClaudeCodeProperties {
    /** Claude Code CLI 실행 명령. 보통 PATH의 claude. */
    private String command = "claude";
    private int timeoutSeconds = 180;
    /** PATH에서 못 찾으면 흔한 설치 위치(npm 전역, ~/.local/bin 등)도 찾아본다. */
    private boolean searchCommonLocations = true;

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public boolean isSearchCommonLocations() { return searchCommonLocations; }
    public void setSearchCommonLocations(boolean searchCommonLocations) { this.searchCommonLocations = searchCommonLocations; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
