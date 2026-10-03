package com.personal.ai.agent.stock;

import java.util.List;

/** 종가 목록(오래된 순)에서 기술 지표를 계산한다. 외부 의존 없음. */
public final class Indicators {

    private static final int RSI_PERIOD = 14;
    private static final int MIN_CLOSES = 61;

    private Indicators() {}

    public static StockSnapshot snapshot(String symbol, List<Double> closes) {
        if (closes.size() < MIN_CLOSES) {
            throw new IllegalArgumentException(symbol + ": 종가가 " + MIN_CLOSES + "개 이상 필요합니다. (" + closes.size() + ")");
        }
        double last = closes.get(closes.size() - 1);
        double month = closes.get(Math.max(0, closes.size() - 22));
        double ma20 = average(closes, 20);
        return new StockSnapshot(symbol, round(last), round((last - month) / month * 100), round(rsi(closes)),
                round(average(closes, 5)), round(ma20), round(average(closes, 60)), round(zscore(closes, 20, last, ma20)));
    }

    static double average(List<Double> closes, int days) {
        return closes.subList(closes.size() - days, closes.size()).stream().mapToDouble(d -> d).average().orElseThrow();
    }

    static double zscore(List<Double> closes, int days, double last, double mean) {
        double variance = closes.subList(closes.size() - days, closes.size()).stream()
                .mapToDouble(d -> (d - mean) * (d - mean)).average().orElseThrow();
        return variance == 0 ? 0 : (last - mean) / Math.sqrt(variance);
    }

    /** Wilder RSI. */
    static double rsi(List<Double> closes) {
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= RSI_PERIOD; i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            if (diff > 0) gain += diff; else loss -= diff;
        }
        gain /= RSI_PERIOD;
        loss /= RSI_PERIOD;
        for (int i = RSI_PERIOD + 1; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            gain = (gain * (RSI_PERIOD - 1) + Math.max(diff, 0)) / RSI_PERIOD;
            loss = (loss * (RSI_PERIOD - 1) + Math.max(-diff, 0)) / RSI_PERIOD;
        }
        return loss == 0 ? 100 : 100 - 100 / (1 + gain / loss);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
