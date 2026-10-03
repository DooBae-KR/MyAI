package com.personal.ai.api.stock;

import com.personal.ai.agent.stock.StockDecision;
import com.personal.ai.agent.stock.StockSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 디스코드 웹훅으로 추천 결과를 보낸다. 한 메시지에 embed는 최대 10개라 나눠 보낸다. */
@Component
public class DiscordNotifier {

    private static final int MAX_EMBEDS = 10;

    private final RestClient http = RestClient.create();

    public void send(String webhookUrl, List<Recommendation> items) {
        for (int i = 0; i < items.size(); i += MAX_EMBEDS) {
            List<Map<String, Object>> embeds = items.subList(i, Math.min(i + MAX_EMBEDS, items.size())).stream()
                    .map(DiscordNotifier::embed).toList();
            http.post().uri(webhookUrl).body(Map.of("content", i == 0 ? "📈 오늘의 종목 추천" : "", "embeds", embeds))
                    .retrieve().toBodilessEntity();
        }
    }

    static Map<String, Object> embed(Recommendation r) {
        if (r.decision() == null) {
            return Map.of("title", r.symbol(), "description", "분석 실패: " + r.error(), "color", 0x95a5a6);
        }
        StockDecision d = r.decision();
        StockSnapshot s = r.snapshot();
        String stats = "현재가 %s | 1개월 %+.2f%% | RSI %.1f | Z-score %.2f".formatted(s.price(), s.changePct30d(), s.rsi(), s.zscore20());
        List<String> lines = new ArrayList<>();
        lines.add(stats);
        lines.add(d.reasoning());
        return Map.of("title", "%s — %s (신뢰도 %.0f%%)".formatted(d.symbol(), d.decision(), d.confidence() * 100),
                "description", String.join("\n", lines), "color", color(d.decision()));
    }

    private static int color(String decision) {
        return switch (decision) {
            case "BUY" -> 0x2ecc71;
            case "SELL" -> 0xe74c3c;
            default -> 0xf1c40f;
        };
    }
}
