package com.personal.ai.api.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiResponse;
import com.personal.ai.core.model.LlmProvider;
import com.personal.ai.data.learning.AppSetting;
import com.personal.ai.data.learning.AppSettingRepository;
import com.personal.ai.llm.RoutingAiModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SettingsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AppSettingRepository repo = mock(AppSettingRepository.class);

    private RoutingAiModel router(boolean withClaude, AiModel ollama) {
        Map<LlmProvider, AiModel> models = new EnumMap<>(LlmProvider.class);
        models.put(LlmProvider.OLLAMA, ollama);
        if (withClaude) {
            models.put(LlmProvider.CLAUDE, r -> new AiResponse("확인 (" + r.getModel() + ")"));
        }
        return new RoutingAiModel(models, LlmProvider.OLLAMA, "qwen3:14b", "claude-sonnet-5-5");
    }

    private SettingsService service(RoutingAiModel router) {
        return new SettingsService(router, repo, mapper);
    }

    private HttpStatus statusOf(Runnable call) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
    }

    @Test
    void getReportsCurrentSelectionDefaultsAndClaudeAvailability() {
        LlmSettingsResponse s = service(router(true, r -> new AiResponse("o"))).get();

        assertEquals("OLLAMA", s.provider());
        assertEquals("qwen3:14b", s.activeModel());
        assertTrue(s.claudeAvailable());
        assertEquals("claude-sonnet-5-5", s.defaults().claudeModel());
        assertFalse(service(router(false, r -> new AiResponse("o"))).get().claudeAvailable());
    }

    @Test
    void updateAppliesAndPersistsSelectionWithTrimmedModelNames() {
        RoutingAiModel router = router(true, r -> new AiResponse("o"));
        when(repo.findById(SettingsService.KEY)).thenReturn(Optional.empty());

        LlmSettingsResponse s = service(router).update(new UpdateLlmRequest("claude", "  ", " claude-opus-5-5 "));

        assertEquals("CLAUDE", s.provider());
        assertEquals("claude-opus-5-5", s.activeModel());
        assertNull(s.ollamaModel()); // 비워 두면 기본 모델
        ArgumentCaptor<AppSetting> saved = ArgumentCaptor.forClass(AppSetting.class);
        verify(repo).save(saved.capture());
        assertTrue(saved.getValue().getValue().contains("\"provider\":\"CLAUDE\""));
        assertTrue(saved.getValue().getValue().contains("claude-opus-5-5"));
    }

    @Test
    void updateRejectsBadInputWithoutChangingOrSavingAnything() {
        RoutingAiModel noClaude = router(false, r -> new AiResponse("o"));
        SettingsService noClaudeService = service(noClaude);
        RoutingAiModel withClaude = router(true, r -> new AiResponse("o"));
        SettingsService service = service(withClaude);

        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> noClaudeService.update(new UpdateLlmRequest("CLAUDE", null, null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(new UpdateLlmRequest("GPT", null, null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(new UpdateLlmRequest(null, null, null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(new UpdateLlmRequest("OLLAMA", "a b; rm -rf", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(new UpdateLlmRequest("OLLAMA", "x".repeat(101), null))));

        assertEquals(LlmProvider.OLLAMA, noClaude.selection().provider());
        assertEquals(LlmProvider.OLLAMA, withClaude.selection().provider());
        verify(repo, never()).save(any());
    }

    @Test
    void selectionIsNotAppliedWhenSavingFails() {
        RoutingAiModel router = router(true, r -> new AiResponse("o"));
        when(repo.findById(SettingsService.KEY)).thenReturn(Optional.empty());
        when(repo.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class,
                () -> service(router).update(new UpdateLlmRequest("CLAUDE", null, null)));

        assertEquals(LlmProvider.OLLAMA, router.selection().provider());
    }

    @Test
    void restoreAppliesSavedSelectionAndIgnoresUnusableOrBrokenOnes() {
        RoutingAiModel router = router(true, r -> new AiResponse("o"));
        when(repo.findById(SettingsService.KEY)).thenReturn(Optional.of(new AppSetting(SettingsService.KEY,
                "{\"provider\":\"CLAUDE\",\"ollamaModel\":null,\"claudeModel\":\"claude-opus-5-5\"}")));
        service(router).restore();
        assertEquals(LlmProvider.CLAUDE, router.selection().provider());

        // 저장된 값이 Claude인데 키가 사라진 경우: 기본 선택 유지
        RoutingAiModel keyRemoved = router(false, r -> new AiResponse("o"));
        service(keyRemoved).restore();
        assertEquals(LlmProvider.OLLAMA, keyRemoved.selection().provider());

        // 깨진 JSON: 시작이 실패하면 안 된다
        when(repo.findById(SettingsService.KEY)).thenReturn(Optional.of(new AppSetting(SettingsService.KEY, "{깨짐")));
        RoutingAiModel broken = router(true, r -> new AiResponse("o"));
        assertDoesNotThrow(() -> service(broken).restore());
        assertEquals(LlmProvider.OLLAMA, broken.selection().provider());
    }

    @Test
    void testReportsSuccessWithReplyAndModel() {
        RoutingAiModel router = router(true, r -> new AiResponse("o"));
        router.select(new RoutingAiModel.Selection(LlmProvider.CLAUDE, null, "claude-opus-5-5"));

        LlmTestResponse result = service(router).test();

        assertTrue(result.ok());
        assertEquals("CLAUDE", result.provider());
        assertEquals("claude-opus-5-5", result.model());
        assertEquals("확인 (claude-opus-5-5)", result.reply());
        assertNull(result.message());
    }

    @Test
    void testReportsFailureReasonInsteadOfThrowing() {
        RoutingAiModel router = router(false, r -> { throw new ResourceAccessException("Connection refused"); });

        LlmTestResponse result = service(router).test();

        assertFalse(result.ok());
        assertEquals("OLLAMA", result.provider());
        assertTrue(result.message().contains("Connection refused"));
        assertNull(result.reply());
    }
}
