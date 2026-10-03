package com.personal.ai.api.stock;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** stock.* 설정. watchlist는 야후 파이낸스 심볼(예: 453810.KS, SPY). */
@ConfigurationProperties("stock")
public record StockProperties(boolean enabled, String cron, String discordWebhookUrl, List<String> watchlist) {

    public StockProperties {
        watchlist = watchlist == null ? List.of() : watchlist;
    }
}
