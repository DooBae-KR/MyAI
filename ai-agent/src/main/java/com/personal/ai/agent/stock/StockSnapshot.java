package com.personal.ai.agent.stock;

/** 한 종목의 현재 시세와 기술 지표. LLM 입력으로 JSON 직렬화된다. */
public record StockSnapshot(String symbol, double price, double changePct30d, double rsi,
                            double ma5, double ma20, double ma60, double zscore20) {
}
