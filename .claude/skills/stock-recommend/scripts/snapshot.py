#!/usr/bin/env python3
"""야후 파이낸스 일봉으로 종목별 기술 지표를 계산해 JSON으로 출력한다. 표준 라이브러리만 사용.

사용: snapshot.py 453810.KS SPY   (인자가 없으면 STOCK_WATCHLIST 환경변수를 쉼표로 분리해 사용)
"""
import json
import math
import os
import sys
import urllib.request

RSI_PERIOD = 14
MIN_CLOSES = 61


def closes(symbol):
    req = urllib.request.Request(
        f"https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=6mo&interval=1d",
        headers={"User-Agent": "Mozilla/5.0"},
    )
    with urllib.request.urlopen(req, timeout=20) as res:
        data = json.load(res)
    quote = data["chart"]["result"][0]["indicators"]["quote"][0]["close"]
    return [c for c in quote if c is not None]  # 휴장일은 null


def rsi(c):
    gain = loss = 0.0
    for i in range(1, RSI_PERIOD + 1):
        d = c[i] - c[i - 1]
        gain, loss = gain + max(d, 0), loss + max(-d, 0)
    gain, loss = gain / RSI_PERIOD, loss / RSI_PERIOD
    for i in range(RSI_PERIOD + 1, len(c)):
        d = c[i] - c[i - 1]
        gain = (gain * (RSI_PERIOD - 1) + max(d, 0)) / RSI_PERIOD
        loss = (loss * (RSI_PERIOD - 1) + max(-d, 0)) / RSI_PERIOD
    return 100.0 if loss == 0 else 100 - 100 / (1 + gain / loss)


def snapshot(symbol, c):
    if len(c) < MIN_CLOSES:
        raise ValueError(f"종가가 {MIN_CLOSES}개 이상 필요합니다. ({len(c)})")
    last = c[-1]
    ma = lambda n: sum(c[-n:]) / n
    mean20 = ma(20)
    std20 = math.sqrt(sum((x - mean20) ** 2 for x in c[-20:]) / 20)
    month = c[max(0, len(c) - 22)]
    r = lambda v: round(v, 2)
    return {
        "symbol": symbol, "price": r(last), "changePct30d": r((last - month) / month * 100),
        "rsi": r(rsi(c)), "ma5": r(ma(5)), "ma20": r(mean20), "ma60": r(ma(60)),
        "zscore20": r((last - mean20) / std20) if std20 else 0.0,
    }


def main():
    symbols = sys.argv[1:] or [s.strip() for s in os.getenv("STOCK_WATCHLIST", "").split(",") if s.strip()]
    if not symbols:
        sys.exit("종목이 없습니다. 인자 또는 STOCK_WATCHLIST를 지정하세요.")
    out = []
    for s in symbols:
        try:
            out.append(snapshot(s, closes(s)))
        except Exception as e:  # 한 종목이 실패해도 나머지는 계속한다
            out.append({"symbol": s, "error": str(e)})
    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
