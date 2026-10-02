package com.personal.ai.api.settings;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/llm")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public LlmSettingsResponse get() {
        return settings.get();
    }

    @PutMapping
    public LlmSettingsResponse update(@RequestBody UpdateLlmRequest request) {
        return settings.update(request);
    }

    /** Claude Code를 다시 찾는다. 못 찾으면 응답의 claudeCodeProblem에 이유가 있다. */
    @PostMapping("/claude-code/refresh")
    public LlmSettingsResponse refreshClaudeCode() {
        return settings.refreshClaudeCode();
    }

    /** 현재 선택으로 짧은 호출을 보내 동작을 확인한다. 실패해도 200이고 ok=false로 사유를 알려 준다. */
    @PostMapping("/test")
    public LlmTestResponse test() {
        return settings.test();
    }
}
