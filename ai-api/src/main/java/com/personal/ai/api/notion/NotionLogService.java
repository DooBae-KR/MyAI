package com.personal.ai.api.notion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.api.learning.DashboardService;
import com.personal.ai.api.stats.StatsResponse;
import com.personal.ai.api.stats.StatsService;
import com.personal.ai.api.today.TodayResponse;
import com.personal.ai.api.today.TodayService;
import com.personal.ai.data.learning.AppSetting;
import com.personal.ai.data.learning.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 하루 학습 로그를 Notion 데이터베이스에 페이지로 남긴다(오늘의 학습 체크리스트, 오늘 활동, 진행 현황).
 * 자동 실행은 하루에 한 번만(마지막으로 남긴 날짜를 app_setting에 기록), 수동 테스트는 항상 새 페이지를 만든다.
 */
@Service
public class NotionLogService {

    private static final Logger log = LoggerFactory.getLogger(NotionLogService.class);
    static final String LAST_LOG_KEY = "notion.lastLogDate";
    static final int MAX_TODOS = 30;
    private static final Pattern DATABASE_ID = Pattern.compile("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final Pattern TOKEN = Pattern.compile("\\S{20,}");

    private final TodayService today;
    private final StatsService stats;
    private final DashboardService dashboard;
    private final AppSettingRepository settings;
    private final Clock clock;
    private final ZoneId zone;
    private final NotionClient client;
    private final String databaseId;
    private final String titleProperty;

    @org.springframework.beans.factory.annotation.Autowired
    public NotionLogService(TodayService today, StatsService stats, DashboardService dashboard, AppSettingRepository settings,
                            Clock clock, ObjectMapper mapper,
                            @Value("${notion.token:}") String token,
                            @Value("${notion.database-id:}") String databaseId,
                            @Value("${notion.title-property:Name}") String titleProperty,
                            @Value("${notion.zone:Asia/Seoul}") String zone) {
        this(today, stats, dashboard, settings, clock, zone, titleProperty, databaseId, build(mapper, token, databaseId, null));
    }

    /** 테스트용: 이미 만든 클라이언트를 받는다. */
    NotionLogService(TodayService today, StatsService stats, DashboardService dashboard, AppSettingRepository settings,
                     Clock clock, String zone, String titleProperty, String databaseId, NotionClient client) {
        this.today = today;
        this.stats = stats;
        this.dashboard = dashboard;
        this.settings = settings;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
        this.titleProperty = titleProperty;
        this.databaseId = databaseId;
        this.client = client;
    }

    private static NotionClient build(ObjectMapper mapper, String token, String databaseId, String baseUrl) {
        if (token.isBlank() && databaseId.isBlank()) {
            return null;
        }
        if (!TOKEN.matcher(token).matches() || !DATABASE_ID.matcher(databaseId).matches()) {
            log.warn("NOTION_TOKEN 또는 NOTION_DATABASE_ID 형식이 올바르지 않아 Notion 기록을 끕니다.");
            return null;
        }
        return baseUrl == null ? new NotionClient(mapper, token) : new NotionClient(mapper, token, baseUrl);
    }

    public boolean enabled() {
        return client != null;
    }

    @Scheduled(cron = "${notion.cron:0 10 21 * * *}", zone = "${notion.zone:Asia/Seoul}")
    void scheduled() {
        if (!enabled()) return;
        String date = LocalDate.now(clock.withZone(zone)).toString();
        if (settings.findById(LAST_LOG_KEY).map(AppSetting::getValue).filter(date::equals).isPresent()) {
            return; // 오늘은 이미 남겼다
        }
        try {
            write(date);
            settings.save(settings.findById(LAST_LOG_KEY).map(s -> {
                s.setValue(date);
                return s;
            }).orElseGet(() -> new AppSetting(LAST_LOG_KEY, date)));
        } catch (ResponseStatusException e) {
            log.warn("Notion 일일 로그 실패: {}", e.getReason());
        }
    }

    /** 지금 오늘의 로그 페이지를 만든다. 설정이 없으면 409, 전송 실패는 502. @return 페이지 URL(없으면 null) */
    public String logNow() {
        if (!enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "NOTION_TOKEN과 NOTION_DATABASE_ID가 설정되지 않았습니다.");
        }
        return write(LocalDate.now(clock.withZone(zone)).toString());
    }

    private String write(String date) {
        try {
            return client.createPage(databaseId, titleProperty, "학습 로그 " + date, blocks());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Notion 기록에 실패했습니다: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Notion 기록이 중단되었습니다.");
        }
    }

    List<NotionClient.Block> blocks() {
        List<NotionClient.Block> blocks = new ArrayList<>();

        blocks.add(NotionClient.Block.heading("오늘의 학습"));
        List<TodayResponse.Todo> todos = today.today().todos();
        if (todos.isEmpty()) {
            blocks.add(NotionClient.Block.paragraph("지금 해야 할 일이 없습니다."));
        }
        todos.stream().limit(MAX_TODOS).forEach(t -> blocks.add(NotionClient.Block.bullet((t.done() ? "☑ " : "□ ") + t.label())));

        StatsResponse snapshot = stats.stats();
        StatsResponse.Day day = snapshot.activity().get(snapshot.activity().size() - 1);
        blocks.add(NotionClient.Block.heading("오늘 활동"));
        blocks.add(NotionClient.Block.bullet("진단 답변 " + day.answers() + "개"));
        blocks.add(NotionClient.Block.bullet("코딩 풀이 제출 " + day.submissions() + "개"));
        blocks.add(NotionClient.Block.bullet("학습 시간 " + day.studyMinutes() + "분"));

        blocks.add(NotionClient.Block.heading("진행 현황"));
        dashboard.goals().forEach(g -> blocks.add(NotionClient.Block.bullet(
                g.subject() + " " + g.completedSteps() + "/" + g.totalSteps() + " Step (" + g.progressPercent() + "%)")));
        return blocks;
    }
}
