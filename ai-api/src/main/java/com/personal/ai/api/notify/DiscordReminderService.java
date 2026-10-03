package com.personal.ai.api.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.api.learning.DashboardService;
import com.personal.ai.api.learning.GoalDetail;
import com.personal.ai.api.stats.StatsResponse;
import com.personal.ai.api.stats.StatsService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@EnableScheduling
public class DiscordReminderService {

    private static final Logger log = LoggerFactory.getLogger(DiscordReminderService.class);
    private static final String[] ALLOWED_PREFIXES = {"https://discord.com/api/webhooks/", "https://discordapp.com/api/webhooks/"};

    private final DashboardService dashboard;
    private final StatsService stats;
    private final DiscordClient client;

    public DiscordReminderService(DashboardService dashboard, StatsService stats, ObjectMapper mapper,
                                  @Value("${discord.webhook-url:}") String webhookUrl) {
        this.dashboard = dashboard;
        this.stats = stats;
        if (webhookUrl.isBlank()) {
            this.client = null;
        } else if (!isDiscordWebhook(webhookUrl)) {
            log.warn("DISCORD_WEBHOOK_URL이 Discord 웹훅 주소 형식이 아니어서 알림을 끕니다.");
            this.client = null;
        } else {
            this.client = new DiscordClient(mapper, webhookUrl);
        }
    }

    static boolean isDiscordWebhook(String url) {
        for (String p : ALLOWED_PREFIXES) if (url.startsWith(p)) return true;
        return false;
    }

    public boolean enabled() {
        return client != null;
    }

    @Scheduled(cron = "${discord.cron:0 0 21 * * *}", zone = "${discord.zone:Asia/Seoul}")
    void scheduled() {
        if (!enabled()) return;
        try {
            sendNow();
        } catch (ResponseStatusException e) {
            log.warn("Discord 일일 알림 실패: {}", e.getReason());
        }
    }

    /** 지금 알림을 보낸다. 설정이 없으면 409, 전송 실패는 502로 알린다. */
    public void sendNow() {
        if (!enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DISCORD_WEBHOOK_URL이 설정되지 않았습니다.");
        }
        try {
            client.send(message());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Discord 전송에 실패했습니다: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Discord 전송이 중단되었습니다.");
        }
    }

    String message() {
        var today = stats.stats().activity();
        StatsResponse.Day last = today.get(today.size() - 1);
        var goals = dashboard.goals().stream().map(g -> dashboard.goal(g.goalId())).toList();
        return ReminderMessage.build(goals, last);
    }
}
