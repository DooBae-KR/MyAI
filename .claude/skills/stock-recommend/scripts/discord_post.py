#!/usr/bin/env python3
"""표준입력의 추천 JSON 배열을 Discord 웹훅으로 보낸다. 표준 라이브러리만 사용.

입력: [{"symbol","decision":"BUY|SELL|HOLD","confidence":0.7,"reasoning","stats":"한 줄 요약"}, ...]
         실패 종목은 {"symbol","error"}
환경변수: DISCORD_WEBHOOK_URL
"""
import json
import os
import sys
import urllib.request

COLORS = {"BUY": 0x2ECC71, "SELL": 0xE74C3C, "HOLD": 0xF1C40F}
MAX_EMBEDS = 10  # 메시지당 embed 상한


def embed(item):
    if "error" in item:
        return {"title": item["symbol"], "description": f"분석 실패: {item['error']}", "color": 0x95A5A6}
    d = item["decision"].upper()
    return {
        "title": f"{item['symbol']} — {d} (신뢰도 {round(item['confidence'] * 100)}%)",
        "description": f"{item.get('stats', '')}\n{item['reasoning']}".strip()[:4000],
        "color": COLORS.get(d, 0x95A5A6),
    }


def post(url, payload):
    req = urllib.request.Request(
        url, data=json.dumps(payload).encode(), method="POST",
        headers={"Content-Type": "application/json", "User-Agent": "stock-recommend"},
    )
    urllib.request.urlopen(req, timeout=20).read()


def main():
    url = os.getenv("DISCORD_WEBHOOK_URL", "").strip()
    if not url:
        sys.exit("DISCORD_WEBHOOK_URL이 설정되지 않았습니다.")
    embeds = [embed(i) for i in json.load(sys.stdin)]
    for i in range(0, len(embeds), MAX_EMBEDS):
        post(url, {"content": "📈 오늘의 종목 추천" if i == 0 else "", "embeds": embeds[i:i + MAX_EMBEDS]})
    print(f"{len(embeds)}개 전송 완료")


if __name__ == "__main__":
    main()
