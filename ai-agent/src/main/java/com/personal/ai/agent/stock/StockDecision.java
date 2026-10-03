package com.personal.ai.agent.stock;

public record StockDecision(String symbol, String decision, double confidence, String reasoning, String promptVersion) {
}
