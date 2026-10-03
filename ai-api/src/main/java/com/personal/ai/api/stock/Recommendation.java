package com.personal.ai.api.stock;

import com.personal.ai.agent.stock.StockDecision;
import com.personal.ai.agent.stock.StockSnapshot;

/** 종목 하나의 결과. 실패하면 decision이 null이고 error에 사유가 있다. */
public record Recommendation(String symbol, StockSnapshot snapshot, StockDecision decision, String error) {

    static Recommendation ok(StockSnapshot snapshot, StockDecision decision) {
        return new Recommendation(snapshot.symbol(), snapshot, decision, null);
    }

    static Recommendation failed(String symbol, String error) {
        return new Recommendation(symbol, null, null, error);
    }
}
