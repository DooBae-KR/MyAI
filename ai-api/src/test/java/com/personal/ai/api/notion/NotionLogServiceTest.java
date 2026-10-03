package com.personal.ai.api.notion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.api.learning.DashboardService;
import com.personal.ai.api.learning.GoalSummary;
import com.personal.ai.api.stats.StatsResponse;
import com.personal.ai.api.stats.StatsService;
import com.personal.ai.api.today.TodayResponse;
import com.personal.ai.api.today.TodayService;
import com.personal.ai.data.learning.AppSetting;
import com.personal.ai.data.learning.AppSettingRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class NotionLogServiceTest {

    private static final String DB = "0123456789abcdef0123456789abcdef";
    private final TodayService today = mock(TodayService.class);
    private final StatsService stats = mock(StatsService.class);
    private final DashboardService dashboard = mock(DashboardService.class);
    private final AppSettingRepository settings = mock(AppSettingRepository.class);
    private final NotionClient client = mock(NotionClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC); // 서울 21:00, 10-03
    private final NotionLogService service = new NotionLogService(today, stats, dashboard, settings, clock, "Asia/Seoul", "이름", DB, client);

    private void data() {
        when(today.today()).thenReturn(new TodayResponse(List.of(
                new TodayResponse.Todo("PASSED", "Vue · Step 3 합격 (92점)", "#/goals/1", true),
                new TodayResponse.Todo("STEP", "Vue · Step 4 학습 시작", "#/goals/1", false)), null));
        var days = List.of(new StatsResponse.Day("2026-10-03", 2, 1, 45));
        when(stats.stats()).thenReturn(new StatsResponse(new StatsResponse.Totals(1, 6, 3, 9, 1, 2, 90), days, List.of(), List.of(), List.of(), List.of(), List.of()));
        when(dashboard.goals()).thenReturn(List.of(new GoalSummary(1L, "Vue", "실무", null, null, null, 6, 3, 50)));
    }

    @Test
    void blocksHaveChecklistActivityAndProgress() {
        data();

        List<String> texts = service.blocks().stream().map(NotionClient.Block::text).toList();

        assertEquals(List.of("오늘의 학습", "☑ Vue · Step 3 합격 (92점)", "□ Vue · Step 4 학습 시작", "오늘 활동", "진단 답변 2개",
                "코딩 풀이 제출 1개", "학습 시간 45분", "진행 현황", "Vue 3/6 Step (50%)"), texts);
    }

    @Test
    void logNowCreatesATitledPageAndReturnsItsUrlWithoutTouchingTheDailyMarker() throws Exception {
        data();
        when(client.createPage(eq(DB), eq("이름"), eq("학습 로그 2026-10-03"), anyList())).thenReturn("https://notion.so/p");

        assertEquals("https://notion.so/p", service.logNow());
        verify(settings, never()).save(any());
    }

    @Test
    void scheduledRunWritesOncePerDayAndRemembersTheDate() throws Exception {
        data();
        when(settings.findById(NotionLogService.LAST_LOG_KEY)).thenReturn(Optional.empty());

        service.scheduled();

        verify(client, times(1)).createPage(any(), any(), eq("학습 로그 2026-10-03"), anyList());
        ArgumentCaptor<AppSetting> saved = ArgumentCaptor.forClass(AppSetting.class);
        verify(settings).save(saved.capture());
        assertEquals("2026-10-03", saved.getValue().getValue());

        when(settings.findById(NotionLogService.LAST_LOG_KEY)).thenReturn(Optional.of(new AppSetting(NotionLogService.LAST_LOG_KEY, "2026-10-03")));
        service.scheduled(); // 오늘 이미 남겼으니 건너뜀
        verify(client, times(1)).createPage(any(), any(), any(), anyList());
    }

    @Test
    void failedScheduledRunDoesNotMarkTheDayAsDone() throws Exception {
        data();
        when(settings.findById(NotionLogService.LAST_LOG_KEY)).thenReturn(Optional.empty());
        when(client.createPage(any(), any(), any(), anyList())).thenThrow(new IOException("Notion이 거절했습니다 (HTTP 401)"));

        service.scheduled(); // 예외를 밖으로 던지지 않고(스케줄러 보호) 다음 기회에 다시 시도한다

        verify(settings, never()).save(any());
    }

    @Test
    void manualFailureIsA502AndDisabledIsA409() throws Exception {
        data();
        when(client.createPage(any(), any(), any(), anyList())).thenThrow(new IOException("HTTP 401"));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class, service::logNow).getStatusCode());

        var disabled = new NotionLogService(today, stats, dashboard, settings, clock, "Asia/Seoul", "Name", "", null);
        assertFalse(disabled.enabled());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, disabled::logNow).getStatusCode());
        disabled.scheduled(); // 꺼져 있으면 아무것도 하지 않는다
        assertTrue(service.enabled());
    }

    @Test
    void invalidTokenOrDatabaseIdDisablesTheIntegrationInsteadOfFailingStartup() {
        var mapper = new ObjectMapper();
        assertFalse(new NotionLogService(today, stats, dashboard, settings, clock, mapper, "short", DB, "Name", "Asia/Seoul").enabled());
        assertFalse(new NotionLogService(today, stats, dashboard, settings, clock, mapper, "secret_abcdefghijklmnopqrstuvwxyz", "not-an-id", "Name", "Asia/Seoul").enabled());
        assertFalse(new NotionLogService(today, stats, dashboard, settings, clock, mapper, "", "", "Name", "Asia/Seoul").enabled());
        assertTrue(new NotionLogService(today, stats, dashboard, settings, clock, mapper, "secret_abcdefghijklmnopqrstuvwxyz", "01234567-89ab-cdef-0123-456789abcdef", "Name", "Asia/Seoul").enabled());
    }
}
