package com.personal.ai.api.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personal.ai.api.stats.StatsResponse;
import com.personal.ai.api.stats.StatsService;
import com.personal.ai.api.today.TodayResponse;
import com.personal.ai.api.today.TodayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Discord 슬래시 명령(/today, /next, /stats)에 답한다. 개인 학습 데이터를 보여 주므로 허용한 사용자 ID만 쓸 수 있고,
 * 답은 명령한 사람에게만 보이는(ephemeral) 메시지로 보낸다. LLM을 호출하지 않아 Discord의 3초 제한 안에 끝난다.
 */
@Service
public class DiscordCommandService {

    static final int MAX_LENGTH = 2000;
    private static final int PING = 1;
    private static final int APPLICATION_COMMAND = 2;
    private static final int PONG = 1;
    private static final int CHANNEL_MESSAGE = 4;
    private static final int EPHEMERAL = 64;

    private final TodayService today;
    private final StatsService stats;
    private final ObjectMapper mapper;
    private final String allowedUserId;

    public DiscordCommandService(TodayService today, StatsService stats, ObjectMapper mapper,
                                 @Value("${discord.allowed-user-id:}") String allowedUserId) {
        this.today = today;
        this.stats = stats;
        this.mapper = mapper;
        this.allowedUserId = allowedUserId.trim();
    }

    /** 상호작용 JSON에 대한 응답 JSON을 만든다. */
    public ObjectNode handle(JsonNode interaction) {
        int type = interaction.path("type").asInt();
        if (type == PING) {
            return mapper.createObjectNode().put("type", PONG);
        }
        if (type != APPLICATION_COMMAND) {
            return message("지원하지 않는 상호작용입니다.");
        }
        // 서버(길드)에서는 member.user, DM에서는 user에 호출자가 들어 있다
        String caller = interaction.path("member").path("user").path("id").asText(interaction.path("user").path("id").asText(""));
        if (allowedUserId.isEmpty()) {
            return message("DISCORD_ALLOWED_USER_ID가 설정되지 않아 명령을 쓸 수 없습니다. 서버 설정을 확인하세요.");
        }
        if (!allowedUserId.equals(caller)) {
            return message("허용된 사용자가 아닙니다.");
        }
        return switch (interaction.path("data").path("name").asText()) {
            case "today" -> message(todayText());
            case "next" -> message(nextText());
            case "stats" -> message(statsText());
            default -> message("알 수 없는 명령입니다. /today, /next, /stats를 쓸 수 있습니다.");
        };
    }

    String todayText() {
        List<TodayResponse.Todo> todos = today.today().todos();
        if (todos.isEmpty()) return "📚 **오늘의 학습**\n지금 해야 할 일이 없습니다.";
        StringBuilder sb = new StringBuilder("📚 **오늘의 학습**\n");
        todos.forEach(t -> sb.append(t.done() ? "☑ " : "□ ").append(t.label()).append('\n'));
        return sb.toString().stripTrailing();
    }

    String nextText() {
        return today.today().todos().stream().filter(t -> !t.done()).findFirst()
                .map(t -> "👉 **다음 할 일**\n" + t.label())
                .orElse("🎉 오늘 할 일을 모두 마쳤습니다.");
    }

    String statsText() {
        StatsResponse s = stats.stats();
        StatsResponse.Totals t = s.totals();
        StatsResponse.Day day = s.activity().get(s.activity().size() - 1);
        return "📊 **학습 통계**\n"
                + "• 완료한 Step " + t.stepsCompleted() + "/" + t.stepsTotal() + "\n"
                + "• 학습 시간 총 " + t.studyMinutes() + "분 (오늘 " + day.studyMinutes() + "분)\n"
                + "• 오늘 진단 답변 " + day.answers() + "개, 코딩 풀이 " + day.submissions() + "개\n"
                + "• 복습할 Step " + s.reviews().size() + "개";
    }

    /** 명령한 사람에게만 보이는 메시지. 본문에 멘션이 섞여도 아무도 호출하지 않는다. */
    private ObjectNode message(String text) {
        ObjectNode data = mapper.createObjectNode()
                .put("content", text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 1) + "…")
                .put("flags", EPHEMERAL);
        data.putObject("allowed_mentions").putArray("parse");
        ObjectNode out = mapper.createObjectNode().put("type", CHANNEL_MESSAGE);
        out.set("data", data);
        return out;
    }
}
