---
name: stock-recommend
description: >
  watchlist 종목의 시세·기술 지표를 보고 BUY/SELL/HOLD를 추천해 Discord로 보낸다.
  "종목 추천", "오늘 종목", "디스코드로 종목 보내줘", /stock-recommend 에 사용.
  API 키 없이 Claude Code 구독으로 동작한다.
---

# stock-recommend

종목 하나하나를 직접 판단해 Discord 웹훅으로 보낸다. 시세 조회와 전송은 스크립트가, 판단은 Claude가 한다.

## 준비
- `DISCORD_WEBHOOK_URL`: Discord 채널 설정 > 연동 > 웹후크 URL. 환경변수로 둔다. 커밋 금지.
- `STOCK_WATCHLIST`: 야후 심볼을 쉼표로. 예 `453810.KS,SPY`. 사용자가 종목을 말하면 그 종목을 우선한다.
- Python 3 필요. 추가 설치 없음.

## 절차
1. 지표 수집. 종목 인자는 생략하면 `STOCK_WATCHLIST`를 쓴다.
   ```bash
   python3 .claude/skills/stock-recommend/scripts/snapshot.py 453810.KS SPY
   ```
   출력은 종목별 `price, changePct30d, rsi, ma5, ma20, ma60, zscore20`. 실패 종목은 `error`가 들어 있다.
2. 종목마다 아래 규칙으로 판단한다.
   - 적립식 장기투자가 전제다. 단기 변동만으로 SELL하지 않는다. 신호가 엇갈리거나 약하면 HOLD.
   - RSI 30 이하 과매도, 70 이상 과매수. 이동평균 배열과 zscore20(20일 평균 대비 표준편차 배수)을 함께 본다.
   - 입력 지표에 없는 뉴스·실적·거시 상황을 아는 것처럼 쓰지 않는다.
   - `confidence`는 0~1. 신호가 일관될수록 높게, 엇갈리면 0.5 이하.
   - `reasoning`은 한국어 2~3문장.
3. 결과를 JSON 배열로 만들어 전송한다.
   ```bash
   python3 .claude/skills/stock-recommend/scripts/discord_post.py <<'JSON'
   [{"symbol":"SPY","decision":"HOLD","confidence":0.55,"stats":"현재가 500 | 1개월 +1.2% | RSI 52 | Z 0.3","reasoning":"..."}]
   JSON
   ```
   `snapshot.py`가 `error`를 낸 종목은 `{"symbol":"...","error":"..."}`로 그대로 넘긴다.
4. 사용자에게 종목별 결정을 한 줄씩 요약하고 전송 성공 여부를 알린다.

## 주의
- 참고용 분석이다. 투자 판단과 책임은 사용자에게 있다. 추천에 단정적 수익 보장 표현을 쓰지 않는다.
- 웹훅 URL이 없으면 전송하지 말고 설정 방법을 안내한다.
- 정기 발송은 Claude Code 루틴(예약 작업)으로 이 스킬을 호출하게 만든다. 루틴 환경에도 `DISCORD_WEBHOOK_URL`이 있어야 한다.
