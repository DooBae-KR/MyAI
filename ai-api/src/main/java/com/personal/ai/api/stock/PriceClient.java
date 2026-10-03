package com.personal.ai.api.stock;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/** 야후 파이낸스 일봉 종가(오래된 순). API 키가 필요 없다. */
@Component
public class PriceClient {

    private final RestClient http = RestClient.builder()
            .baseUrl("https://query1.finance.yahoo.com")
            .defaultHeader("User-Agent", "Mozilla/5.0")
            .build();

    public List<Double> closes(String symbol) {
        JsonNode root = http.get().uri("/v8/finance/chart/{s}?range=6mo&interval=1d", symbol)
                .retrieve().body(JsonNode.class);
        List<Double> closes = new ArrayList<>();
        for (JsonNode close : root.path("chart").path("result").path(0).path("indicators").path("quote").path(0).path("close")) {
            if (!close.isNull()) { // 휴장일 등은 null로 온다
                closes.add(close.asDouble());
            }
        }
        return closes;
    }
}
