#!/usr/bin/env python3
"""universe.txt의 종목을 훑어 기술 지표 기준 후보를 JSON으로 출력한다. 표준 라이브러리만 사용.

사용: screen.py [개수=15]   (점수가 높은 순. 최종 추천은 Claude가 고른다)
"""
import json
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from snapshot import closes, snapshot

UNIVERSE = Path(__file__).resolve().parent.parent / "universe.txt"


def load_universe():
    items = []
    for line in UNIVERSE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            symbol, _, name = line.partition(",")
            items.append((symbol.strip(), name.strip()))
    return items


def score(s):
    """장기 적립 관점의 단순 휴리스틱: 상승 추세 + 과열 아님 + 눌림."""
    pts = 0.0
    pts += 1 if s["price"] > s["ma60"] else 0   # 중기 상승 추세
    pts += 1 if s["ma20"] > s["ma60"] else 0    # 이동평균 정배열
    pts += 1 if 30 <= s["rsi"] <= 55 else 0     # 과열이 아닌 구간
    pts += 0.5 if s["zscore20"] < 0 else 0      # 20일 평균 아래 = 눌림
    pts -= 1 if s["rsi"] > 70 else 0            # 과매수 감점
    return pts


def tags(s):
    out = []
    if s["rsi"] <= 30:
        out.append("과매도")
    if s["rsi"] >= 70:
        out.append("과매수")
    if s["price"] > s["ma60"] and s["zscore20"] < 0:
        out.append("상승추세 눌림")
    if s["price"] < s["ma60"] and s["ma20"] < s["ma60"]:
        out.append("하락추세")
    return out


def scan(item):
    symbol, name = item
    try:
        s = snapshot(symbol, closes(symbol))
        return {"name": name, **s, "score": score(s), "tags": tags(s)}
    except Exception as e:  # 한 종목이 실패해도 나머지는 계속한다
        return {"symbol": symbol, "name": name, "error": str(e)}


def main():
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 15
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(scan, load_universe()))
    ok = sorted((r for r in results if "error" not in r), key=lambda r: -r["score"])
    failed = [{"symbol": r["symbol"], "error": r["error"]} for r in results if "error" in r]
    print(json.dumps({"candidates": ok[:limit], "scanned": len(ok), "failed": failed}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
