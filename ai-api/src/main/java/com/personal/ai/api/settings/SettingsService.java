package com.personal.ai.api.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.LlmProvider;
import com.personal.ai.data.learning.AppSetting;
import com.personal.ai.data.learning.AppSettingRepository;
import com.personal.ai.llm.RoutingAiModel;
import com.personal.ai.llm.RoutingAiModel.Selection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/** 사용할 LLM을 화면에서 바꾼다. 선택은 DB에 저장해 재시작 후에도 유지한다. API 키는 다루지 않는다(.env). */
@Service
public class SettingsService {

    static final String KEY = "llm.selection";
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._:/@-]{1,100}");
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final RoutingAiModel router;
    private final AppSettingRepository settings;
    private final ObjectMapper mapper;

    public SettingsService(RoutingAiModel router, AppSettingRepository settings, ObjectMapper mapper) {
        this.router = router;
        this.settings = settings;
        this.mapper = mapper;
    }

    /** 시작할 때 저장된 선택을 적용한다. 저장된 값이 쓸 수 없는 상태(예: 키가 사라진 Claude)면 무시하고 기본 선택을 유지한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        settings.findById(KEY).ifPresent(saved -> {
            try {
                router.select(mapper.readValue(saved.getValue(), Selection.class));
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.warn("저장된 LLM 선택을 적용하지 못해 기본 선택을 유지합니다: {}", e.getMessage());
            }
        });
    }

    public LlmSettingsResponse get() {
        Selection s = router.selection();
        var detection = router.claudeCodeDetection();
        return new LlmSettingsResponse(s.provider().name(), s.ollamaModel(), s.claudeModel(), s.claudeCodeModel(),
                router.effectiveModel(s), router.isAvailable(LlmProvider.CLAUDE), router.isAvailable(LlmProvider.CLAUDE_CODE),
                detection == null ? null : detection.command(),
                detection == null || detection.found() ? null : detection.problem(),
                new LlmSettingsResponse.Defaults(router.defaultModel(LlmProvider.OLLAMA), router.defaultModel(LlmProvider.CLAUDE)));
    }

    /** 앱을 다시 시작하지 않고 Claude Code를 다시 찾는다(설치나 로그인 환경을 고친 뒤). */
    public LlmSettingsResponse refreshClaudeCode() {
        router.redetectClaudeCode();
        return get();
    }

    public LlmSettingsResponse update(UpdateLlmRequest request) {
        LlmProvider provider;
        try {
            provider = LlmProvider.valueOf(String.valueOf(request.provider()).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw badRequest("provider는 OLLAMA, CLAUDE, CLAUDE_CODE 중 하나여야 합니다.");
        }
        if (!router.isAvailable(provider)) {
            throw badRequest(provider == LlmProvider.CLAUDE_CODE
                    ? "Claude Code CLI(claude)를 찾을 수 없어 선택할 수 없습니다. 설치하고 터미널에서 claude 로 로그인한 뒤 앱을 다시 시작하세요."
                    : "Claude API 키가 설정되지 않아 Claude를 선택할 수 없습니다. .env의 ANTHROPIC_API_KEY를 설정하고 앱을 다시 시작하세요.");
        }
        Selection next = new Selection(provider, modelName(request.ollamaModel()), modelName(request.claudeModel()),
                modelName(request.claudeCodeModel()));

        try {
            String json = mapper.writeValueAsString(next);
            AppSetting row = settings.findById(KEY).orElseGet(() -> new AppSetting(KEY, json));
            row.setValue(json);
            settings.save(row);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        router.select(next); // 저장이 성공한 뒤에만 적용한다
        return get();
    }

    /** 현재 선택으로 아주 짧은 호출을 보내 키/모델/연결이 동작하는지 확인한다. */
    public LlmTestResponse test() {
        Selection s = router.selection();
        String model = router.effectiveModel(s);
        AiRequest request = new AiRequest();
        request.setUserPrompt("연결 테스트입니다. '확인'이라고만 답하세요.");
        long start = System.nanoTime();
        try {
            String reply = router.chat(request).getContent();
            return new LlmTestResponse(true, s.provider().name(), model, elapsedMs(start),
                    reply == null ? "" : reply.strip().substring(0, Math.min(80, reply.strip().length())), null);
        } catch (RuntimeException e) {
            return new LlmTestResponse(false, s.provider().name(), model, elapsedMs(start), null, e.getMessage());
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** 비어 있으면 null(기본 모델). */
    private static String modelName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String name = value.trim();
        if (!MODEL_NAME.matcher(name).matches()) {
            throw badRequest("모델 이름은 영문, 숫자, . _ : / @ - 만 쓸 수 있고 100자 이하여야 합니다: " + name);
        }
        return name;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
