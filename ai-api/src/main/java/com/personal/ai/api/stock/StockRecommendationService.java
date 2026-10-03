package com.personal.ai.api.stock;

import com.personal.ai.agent.stock.Indicators;
import com.personal.ai.agent.stock.StockAgent;
import com.personal.ai.agent.stock.StockSnapshot;
import com.personal.ai.core.model.AiModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** watchlist 종목을 분석해 디스코드로 보낸다. 한 종목이 실패해도 나머지는 계속한다. */
@Service
public class StockRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(StockRecommendationService.class);

    private final StockProperties properties;
    private final PriceClient prices;
    private final DiscordNotifier discord;
    private final StockAgent agent;

    public StockRecommendationService(StockProperties properties, PriceClient prices, DiscordNotifier discord, AiModel model) {
        this.properties = properties;
        this.prices = prices;
        this.discord = discord;
        this.agent = new StockAgent(model);
    }

    @Scheduled(cron = "${stock.cron:0 0 9 * * MON-FRI}", zone = "Asia/Seoul")
    void scheduled() {
        if (!properties.enabled()) {
            return;
        }
        try {
            run();
        } catch (RuntimeException e) {
            log.error("종목 추천 발송 실패", e);
        }
    }

    public List<Recommendation> run() {
        List<Recommendation> results = new ArrayList<>();
        for (String symbol : properties.watchlist()) {
            try {
                StockSnapshot snapshot = Indicators.snapshot(symbol, prices.closes(symbol));
                results.add(Recommendation.ok(snapshot, agent.decide(snapshot)));
            } catch (RuntimeException e) {
                log.warn("{} 분석 실패: {}", symbol, e.getMessage());
                results.add(Recommendation.failed(symbol, e.getMessage()));
            }
        }
        String webhook = properties.discordWebhookUrl();
        if (webhook == null || webhook.isBlank()) {
            throw new IllegalStateException("stock.discord-webhook-url(DISCORD_WEBHOOK_URL)이 설정되지 않았습니다.");
        }
        if (!results.isEmpty()) {
            discord.send(webhook, results);
        }
        return results;
    }
}
