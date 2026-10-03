---
name: stock-recommend
description: >
  후보 종목 풀을 훑어 지금 살 만한 종목을 직접 골라 추천하고 Discord로 보낸다.
  "종목 추천", "오늘 뭐 살까", "종목 골라줘", "디스코드로 종목 보내줘", /stock-recommend 에 사용.
  API 키 없이 Claude Code 구독으로 동작한다.
---

# stock-recommend

사용자는 종목을 지정하지 않는다. `universe.txt`의 후보 풀을 스크립트가 훑고, Claude가 그중 추천할 종목을 직접 골라 근거와 함께 Discord로 보낸다.

## 준비
- `DISCORD_WEBHOOK_URL`: Discord 채널 설정 > 연동 > 웹후크 URL. 환경변수로 둔다. 커밋 금지.
- Python 3 필요. 추가 설치 없음.
- 후보 풀은 `universe.txt`(`야후심볼,이름`). 한국 대형주·ETF, 미국 ETF·대형주 55개가 들어 있고 자유롭게 고칠 수 있다.

## 절차
1. 후보 수집. 점수가 높은 순으로 15개를 받는다.
   ```bash
   python3 .claude/skills/stock-recommend/scripts/screen.py 15
   ```
   종목별 `price, changePct30d, rsi, ma5, ma20, ma60, zscore20, score, tags`와 조회 실패 목록이 나온다.
2. 후보 중 **3~5개**를 골라 `BUY` 추천으로 낸다. 고르는 기준:
   - 적립식 장기투자 관점. 중기 상승 추세(price > ma60, ma20 > ma60)에서 과열이 아닌(RSI 30~60) 종목, 또는 과매도(RSI 30 이하)에서 반등 조짐이 있는 종목을 우선한다.
   - RSI 70 이상 과매수나 하락 추세 종목은 피한다. 피한 이유가 의미 있으면 `HOLD`로 한 줄 언급해도 된다.
   - 종목 유형(한국/미국, ETF/개별주)이 한쪽으로 쏠리지 않게 섞는다.
   - 입력 지표에 없는 뉴스·실적·거시 상황을 아는 것처럼 쓰지 않는다. 근거는 지표만 쓴다.
   - `confidence`는 0~1. 신호가 일관될수록 높게, 엇갈리면 0.5 이하.
   - `reasoning`은 한국어 2~3문장. 어떤 지표 때문에 골랐는지 쓴다.
   - 후보가 모두 약하면 억지로 채우지 말고 적게 추천하거나 "오늘은 뚜렷한 후보 없음"으로 보낸다.
3. 전송.
   ```bash
   python3 .claude/skills/stock-recommend/scripts/discord_post.py <<'JSON'
   [{"symbol":"006400.KS","name":"삼성SDI","decision":"BUY","confidence":0.6,"stats":"현재가 ... | 1개월 +2.1% | RSI 51 | Z -0.4","reasoning":"..."}]
   JSON
   ```
4. 사용자에게 추천 종목을 한 줄씩 요약하고 전송 성공 여부를 알린다.

## 특정 종목 분석
사용자가 종목을 직접 말하면(예: "삼성전자 어때?") 후보 풀 대신 해당 종목만 본다.
```bash
python3 .claude/skills/stock-recommend/scripts/snapshot.py 005930.KS SPY
```
같은 기준으로 `BUY|SELL|HOLD`를 판단해 3번처럼 전송한다.

## 주의
- 참고용 분석이다. 투자 판단과 책임은 사용자에게 있다. 수익을 보장하는 단정적 표현을 쓰지 않는다.
- 웹훅 URL이 없으면 전송하지 말고 설정 방법을 안내한다.
- 정기 발송은 Claude Code 루틴(예약 작업)이 이 스킬을 호출하게 만든다. 루틴 환경에도 `DISCORD_WEBHOOK_URL`이 있어야 한다.
