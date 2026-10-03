# personal-ai

Qwen3 14B + Ollama + Java/Spring Boot 기반 로컬 AI 프로젝트 1단계 골격입니다.

## 구조

```text
personal-ai/
├── ai-core/
│   └── AiModel / AiRequest / AiResponse
├── ai-llm/
│   └── OllamaAiModel
└── ai-api/
    ├── AiService
    └── AiController
```

## 호출 흐름

```text
POST /api/ai/chat
        ↓
AiController
        ↓
AiService
        ↓
AiModel
        ↓
OllamaAiModel
        ↓
Ollama (localhost:11434)
        ↓
Qwen3 14B
```

## 실행

Ollama가 실행 중이고 Qwen3 14B가 준비되어 있어야 합니다.

```powershell
ollama run qwen3:14b
```

Spring Boot:

```powershell
gradlew.bat :ai-api:bootRun
```

테스트:

```http
POST http://localhost:8080/api/ai/chat
Content-Type: application/json

{
  "systemPrompt": "너는 나의 개인 AI다. 항상 한국어로 답변한다.",
  "userPrompt": "안녕하세요. 자기소개를 해주세요."
}
```

다음 단계에서 ai-agent, ai-memory, ai-mcp 등을 추가합니다.
# MyAI

## MCP 서버

앱이 실행 중이면 `POST /mcp`(Streamable HTTP)로 비서를 MCP 도구(`ask_assistant`)로 쓸 수 있습니다.
설정된 LLM(`AI_PROVIDER`: ollama 또는 claude)이 답합니다.

프로젝트 루트의 `.mcp.json`에 이미 등록돼 있어서, 이 폴더에서 Claude Code를 열면 승인 후 바로 쓸 수 있습니다.
수동 등록은 아래 명령입니다 (프로젝트 루트에서 실행하거나 `--scope project`를 붙이세요).

```bash
claude mcp add --transport http personal-ai http://localhost:8080/mcp
# 토큰을 쓸 때 (.env 의 MCP_TOKEN)
claude mcp add --transport http personal-ai http://localhost:8080/mcp --header "Authorization: Bearer <MCP_TOKEN>"
```

외부에 노출하면 LLM 호출 비용이 발생하므로 `MCP_TOKEN`을 반드시 설정하세요.

## 데이터베이스 (Supabase / PostgreSQL)

앱 실행에는 아래 환경변수가 필요합니다. 프로젝트 루트의 `.env`(Git 제외)를 앱이 직접 읽으므로 IntelliJ, VS Code, 터미널 어디서 실행해도 됩니다.
OS 환경변수나 Run Configuration 값이 있으면 그쪽이 우선합니다. `.env`에는 `KEY=값` 형식으로 쓰고 따옴표는 쓰지 마세요.

```text
DB_HOST=aws-1-ap-south-1.pooler.supabase.com
DB_PORT=5432
DB_NAME=postgres
DB_USERNAME=postgres.<프로젝트-ref>
DB_PASSWORD=<비밀번호>
```

`DB_HOST`, `DB_PORT`(생략하면 5432), `DB_NAME`(생략하면 postgres)으로 `jdbc:postgresql://호스트:포트/이름` 주소를 앱이 조립하므로 `jdbc:` 형식을 직접 쓸 필요가 없습니다.
전체 JDBC 주소를 직접 쓰고 싶으면 `DB_URL=jdbc:postgresql://...`을 주면 **`DB_URL`이 우선**합니다. `DB_URL=`처럼 값을 비운 줄은 두지 마세요.
Supabase Connect 화면의 `postgresql://사용자:비밀번호@호스트:포트/postgres`를 `DB_URL`에 그대로 넣으면 `'url' must start with "jdbc"` 오류가 납니다.
**풀러 주소를 쓰면 `DB_USERNAME`은 반드시 `postgres.<프로젝트-ref>`** 입니다. `postgres`만 쓰면 `no tenant identifier provided`(또는 `Tenant or user not found`)가 납니다.

| 연결 방식 | DB_HOST : DB_PORT | DB_USERNAME |
|---|---|---|
| Session pooler (권장) | `aws-0-<region>.pooler.supabase.com:5432` | `postgres.<프로젝트-ref>` |
| Transaction pooler | `aws-0-<region>.pooler.supabase.com:6543` (`DB_URL`로 쓰고 끝에 `?prepareThreshold=0`) | `postgres.<프로젝트-ref>` |
| 직접 연결 (IPv6 전용일 수 있음) | `db.<프로젝트-ref>.supabase.co:5432` | `postgres` |

시작할 때 DB 설정이 틀렸다면 앱이 원인과 해결 방법을 한국어로 안내합니다(비밀 값은 출력하지 않음).

테이블은 `personal_ai` 스키마에 Flyway(`ai-data/src/main/resources/db/migration`)로 생성됩니다.

## 학습 API

```http
POST /api/learning/subjects
Content-Type: application/json

{ "subject": "Vue", "goal": "실무 수준까지 배우기" }
```

선택 필드: `targetLevel`(`BEGINNER|INTERMEDIATE|ADVANCED`), `deadline`(`2026-12-31`).
같은 이름의 분야는 대소문자 구분 없이 재사용하고 목표만 새로 등록합니다. 응답은 `201`과
`{ "subjectId", "subject", "goalId", "goal" }`, `subject`나 `goal`이 비면 `400`입니다.

```http
POST /api/learning/goals/{goalId}/diagnostic
```

목표의 초기 진단 문제를 설정된 LLM(`AI_PROVIDER`)으로 생성해 저장합니다. 응답은 `201`과
`{ "assessmentId", "goalId", "quiz": { "promptVersion", "questions": [...] } }`입니다(문제에 `keyPoints`는 없습니다).
목표가 없으면 `404`, 모델 응답이 형식을 두 번 어기면 `502`, LLM에 연결할 수 없으면 `503`입니다.
프롬프트는 `ai-agent/src/main/resources/prompts/`에 있고 `promptVersion`이 결과와 함께 저장됩니다.

```http
POST /api/learning/assessments/{assessmentId}/answers
Content-Type: application/json

{ "answers": [ { "questionId": 1, "answer": "..." }, { "questionId": 2, "answer": "..." } ] }
```

진단 답변을 제출하면 LLM이 문제별 점수와 피드백, 정성 평가(`criteria`), 강점/약점을 만들고,
**정답률(난이도 가중), 영역별 점수, 수준(`BEGINNER` < 40 ≤ `INTERMEDIATE` < 75 ≤ `ADVANCED`)은 코드가 계산**합니다.
결과는 진단에 저장되고 목표의 `currentLevel`이 갱신됩니다. 응답은 `201`과 `{ "assessmentId", "result" }`입니다.
- 비어 있는 답변과 생략한 문제는 `0점(미응답)`이며 LLM에는 보내지 않습니다. 답변은 문제당 4000자까지입니다.
- 존재하지 않는 `questionId`, 중복, 채점할 답변 없음은 `400`, 이미 채점된 진단은 `409`입니다.
- 모델 오류(`502`/`503`)로 실패하면 아무것도 저장되지 않아 같은 제출을 다시 보낼 수 있습니다.

```http
POST /api/learning/goals/{goalId}/curriculum
```

채점된 진단 결과(약한 영역 집중, 강한 영역 압축)로 개인 커리큘럼을 만듭니다. 응답은 `201`과
`{ "goalId", "promptVersion", "steps": [ { id, seq, title, objective, difficulty, estimatedMinutes, practiceTasks, status } ] }`이고,
첫 Step만 `AVAILABLE`, 나머지는 `LOCKED`입니다. 목표당 한 번만 만들 수 있고(`409`), 채점된 진단이 없어도 `409`입니다.

## Dashboard (React)

```http
GET /api/learning/goals          목표 목록과 진행률
GET /api/learning/goals/{goalId} 목표 상세: 다음 할 일(nextAction), 진단 결과, Step 목록
```

`nextAction`은 `DIAGNOSTIC_NEEDED → ANSWERS_NEEDED → CURRICULUM_NEEDED → LEARNING` 순서로 바뀝니다.
진행률은 `COMPLETED` Step 수 / 전체 Step 수입니다.

**화면 보는 방법 (둘 중 하나)**

```bash
# A. localhost:8080 하나로 보기 (Spring Boot가 화면까지 서빙)
cd frontend
npm install
npm run build:spring        # 결과가 ai-api/src/main/resources/static 에 생김(Git 제외)
# 그다음 백엔드를 (다시) 실행 → http://localhost:8080

# B. 화면을 고치면서 보기 (즉시 반영, /api 요청은 localhost:8080으로 프록시)
cd frontend
npm run dev                 # http://localhost:5173  (백엔드를 먼저 실행해 둘 것)
```

`localhost:8080`은 화면을 빌드(`build:spring`)하기 전에는 API만 응답합니다. 프론트 코드를 바꾸면 다시 빌드해야 8080에 반영됩니다.
처음 받았거나 `git pull` 뒤에도 `static` 폴더는 Git에 없으니 한 번 빌드해야 합니다.

화면에서 **목표 등록 → 진단 문제 생성 → 답변 제출·채점 → 커리큘럼 생성**까지 모두 할 수 있습니다.
- 진단 문제 화면에는 채점 기준(`keyPoints`)을 내려주지 않습니다(정답 힌트가 되므로). `GET /goals/{id}`는 답변 대기 중인 진단의 문제(`pendingQuiz`)를 함께 줍니다.
- 작성 중인 답변은 브라우저(localStorage)에 임시 저장되어 새로고침이나 오류 후에도 남고, 제출에 성공하면 지워집니다.
- LLM 호출은 모델에 따라 오래 걸릴 수 있어(최대 1~2분) 진행 중 표시를 보여 주고, 실패하면 사유와 함께 다시 시도할 수 있습니다.

## 설정: LLM 선택

대시보드 오른쪽 위의 모델 표시(또는 `#/settings`)에서 **Ollama(로컬), Claude(API), Claude Code(로그인)를 앱 재시작 없이** 바꿀 수 있습니다.
진단 문제 생성, 채점, 커리큘럼 생성, MCP가 모두 선택한 모델로 동작합니다. 모델 이름도 지정할 수 있고(비우면 기본값),
"연결 테스트"로 키와 모델이 동작하는지 바로 확인할 수 있습니다. 선택은 DB(`app_setting`)에 저장되어 재시작 후에도 유지됩니다.

```http
GET  /api/settings/llm         현재 선택, 기본 모델, Claude 사용 가능 여부
PUT  /api/settings/llm         { "provider": "CLAUDE_CODE", "ollamaModel": "", "claudeModel": "", "claudeCodeModel": "sonnet" }
POST /api/settings/llm/test    현재 선택으로 짧은 호출을 보내 확인 (실패해도 200, ok=false와 사유)
```

- **API 키는 화면에서 입력하지 않습니다.** `.env`의 `ANTHROPIC_API_KEY`로만 설정합니다. 비었거나 자리표시자(공백이나 한글이 섞인 값)면 "키 없음"으로 보고 Claude 선택을 막습니다.
- **처음 시작할 때의 기본 LLM**은 `AI_PROVIDER`로 정합니다. 기본값 `auto`는 쓸 수 있는 Claude(Claude Code → Claude API)를 먼저 고르고, 없을 때만 Ollama를 씁니다. `ollama`, `claude`, `claude-code`로 고정할 수도 있습니다.
  **`.env`에 `AI_PROVIDER=ollama`가 있으면 항상 Ollama로 시작합니다.** 의도한 것이 아니면 그 줄을 지우세요. 화면에서 저장한 선택이 있으면 그것이 우선합니다.
- **Claude Code를 못 찾았을 때**: 설정 화면이 이유와 시도한 위치를 보여 줍니다. 앱은 `claude`가 PATH에 없으면 흔한 설치 위치(윈도우는 `%APPDATA%\npm\claude.cmd`, `%USERPROFILE%\.local\bin\claude.exe` 등)도 찾아봅니다. IDE에서 실행한 앱은 터미널과 PATH가 달라 못 찾는 경우가 많습니다.
  설치나 환경을 고친 뒤에는 **"Claude Code 다시 확인" 버튼으로 앱을 다시 시작하지 않고** 확인할 수 있습니다. 경로를 직접 지정하려면 `CLAUDE_CODE_COMMAND`를 쓰세요.
- `AI_PROVIDER`는 처음 시작할 때의 기본 선택이고, 저장된 선택이 있으면 그것이 우선합니다. 저장된 선택이 Claude인데 키가 사라졌다면 무시하고 Ollama로 시작합니다.
- **Claude Code(로그인)** 는 API 키 대신 이 PC의 Claude Code(`claude`) 로그인으로 호출합니다. 앱이 시작할 때 `claude`를 찾으면 선택할 수 있습니다. 모델은 `sonnet`, `opus` 같은 별칭이나 전체 이름이고, 비우면 Claude Code의 기본 모델을 씁니다.
  채점 답변 같은 사용자 입력이 프롬프트에 들어가므로 모델이 이 PC에서 아무것도 하지 못하게 막습니다: 도구 전부 차단, MCP 비활성, 빈 임시 폴더에서 실행, 프롬프트는 인자가 아닌 표준입력으로 전달.
  환경변수의 `ANTHROPIC_API_KEY`는 이 호출에서만 제거합니다(남아 있으면 로그인 대신 API 키가 쓰여 과금됩니다). `claude`가 PATH에 없으면 `CLAUDE_CODE_COMMAND`로 경로를 지정하고, 응답 제한 시간은 `CLAUDE_CODE_TIMEOUTSECONDS`(기본 180)입니다.
- Claude 호출은 비용이 듭니다. `/api` 엔드포인트는 인증이 없으니 외부에 노출하지 마세요.

## 카페 등 다른 PC(노트북)에서 사용하기

Ollama가 없는 노트북에서는 **Claude Code 로그인**으로 쓰는 것이 가장 간단합니다. DB는 Supabase(클라우드)라서 어디서든 같은 데이터를 봅니다.

1. 준비: Git, Java 21, Node.js, Claude Code를 설치합니다.
2. 코드 받기: `git clone`(이미 있다면 `git pull`) 후 `claude/gifted-cerf-fpf6ol` 브랜치로 맞춥니다.
3. `.env` 만들기: **`.env`는 Git에 없으므로 노트북에서 새로 만듭니다.** DB 접속 값(`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`)만 있으면 됩니다. Claude 키나 Ollama 설정은 필요 없습니다.
4. 화면 빌드: `cd frontend && npm install && npm run build:spring`
5. 로그인: 터미널에서 `claude`를 실행해 브라우저로 로그인합니다(한 번만).
6. 앱 실행 후 `http://localhost:8080` → 설정 → **Claude Code (로그인)** 선택 → 저장 → **연결 테스트**.
   처음부터 기본으로 쓰려면 `AI_PROVIDER=claude-code`를 `.env`에 둡니다(없으면 Ollama로 시작).

주의
- **공용 Wi-Fi에서는 DB 포트(5432)가 막힐 수 있습니다.** 접속이 안 되면 휴대폰 핫스팟을 쓰거나 `Test-NetConnection <호스트> -Port 5432`로 확인하고, 막혀 있으면 포트 6543을 시도하세요.
- **`SERVER_ADDRESS=0.0.0.0`으로 바꾸지 마세요.** 기본값(`127.0.0.1`)이어야 같은 Wi-Fi의 다른 사람이 이 서버에 접속하지 못합니다. `/api`에는 인증이 없습니다.
- Claude Code 로그인으로 쓰면 Claude 구독의 사용량 한도가 소모됩니다. 본인 PC에서 본인이 쓰는 용도로만 사용하고, 약관은 직접 확인하세요.
- 로그인이 안 되어 있으면 연결 테스트가 "터미널에서 claude 를 실행해 로그인하세요"라고 안내합니다.

## 사고 패턴 분석 (Phase 6)

진단 답변과 **코딩 풀이에서 반복해서 관찰되는 글쓰기·풀이 습관**을 근거와 함께 가설로 보여 줍니다(`#/patterns`, 헤더의 "사고 패턴").
성격이나 능력을 평가하는 기능이 아닙니다.

```http
POST  /api/learning/patterns/analyze   아직 분석하지 않은 답변(최대 30개)과 코딩 풀이(최대 10개)에서 패턴을 찾는다. 새 답변이 없으면 아무것도 하지 않는다.
GET   /api/learning/patterns           패턴 목록(상태, 신뢰도, 근거, 훈련 제안)
PATCH /api/learning/patterns/{id}      {"status":"DISMISSED"} 기각 / {"status":"HYPOTHESIS"} 되살리기
```

- **LLM과 코드의 역할**: LLM은 패턴과 근거 인용만 제안합니다. **인용이 실제 답변에 있는 문장인지 코드가 검증**해서 지어낸 근거는 버립니다(모두 지어낸 경우 1회 재시도 후 `502`). 신뢰도와 상태는 코드가 근거 개수로 계산합니다.
- **단정하지 않음**: 서로 다른 답변 3개 이상에서 관찰되어야 `SUPPORTED`(반복 관찰됨)이고, 그 전은 `HYPOTHESIS`(가설)입니다.
  신뢰도 = min(0.9, 근거 수/분석한 전체 답변 수) × min(1, 근거 수/5) (근거 3개 미만은 0.4 이하). 답변이 늘어 비율이 낮아지면 신뢰도도 내려갑니다.
- **코딩 풀이 연결**: 제출한 코드·접근 설명·AI 풀이 비교가 같은 분석에 들어갑니다(예: 최단 경로 문제마다 DFS부터 고르는 경향). 근거 인용은 **내가 쓴 코드와 설명에서만** 가능하고 AI 의견은 맥락일 뿐 인용할 수 없습니다. 서로 다른 문제 3개 이상이어야 `SUPPORTED`입니다. 코드는 앞 4000자만 분석에 쓰며, 근거에는 어느 문제의 풀이인지 표시됩니다(V6 마이그레이션).
- **반박**: "맞지 않아요"로 기각하면 이후 분석에서 다시 제안하지 않습니다.
- **훈련 제안**: 패턴마다 "앞으로 N개의 답변에서 ~하기"처럼 구체적이고 측정 가능한 제안을 함께 저장합니다.
- **개선 효과 비교(설계서 12장)**: 이미 알려진 패턴에 대해, 새 답변·풀이에서 학습자가 **훈련 제안대로(패턴과 반대로) 쓴** 경우를 AI가 "개선 신호"로 제안합니다(`improvements`). 관찰 근거와 같은 규칙으로 **인용이 내 글에 실제로 있는지 코드가 검증**하고, 모르는 패턴·기각된 패턴·지어낸 인용은 버립니다. 개선을 억지로 찾지 않도록 프롬프트에서 막았고, 같은 항목이 이미 근거로 쓰였으면 중복 저장하지 않습니다(`pattern_evidence.kind`, V8).
  개선 신호는 **신뢰도·"반복 관찰됨" 판정에 반영하지 않습니다**(관찰 근거만 셈). 효과는 코드가 판단합니다: 가장 최근 근거 3개 중 개선 신호가 2개 이상이면 "개선되는 모습", 1개면 "엇갈림"입니다. 한 번의 좋은 답변을 능력 향상으로 단정하지 않기 위해서입니다.
- **같은 답변을 두 번 분석하지 않습니다.** 분석에 실패하면 답변은 분석 전 상태로 남아 다시 시도할 수 있습니다. 한 번의 분석에서 패턴당 근거는 최대 5개까지 기록합니다.
- **개인정보**: 분석할 때 답변과 채점 결과가 선택한 LLM으로 전달됩니다. Claude Code나 Claude API를 선택하면 Anthropic으로, Ollama를 선택하면 내 PC 안에서만 처리됩니다.

## 코딩테스트 (Phase 7)

화면의 "코딩테스트"(`#/coding`)에서 문제를 등록하면 AI 특강을 만들고, 내 풀이를 권장 접근과 비교해 줍니다.

```http
GET  /api/coding/problems                  문제 목록(특강 유무, 제출 수)
POST /api/coding/problems                  문제 등록 (title 필수)
GET  /api/coding/problems/{id}             문제, 특강, 제출 기록
POST /api/coding/problems/{id}/lecture     AI 특강(17단계) 생성. 다시 호출하면 새로 만들고 가장 최근 것이 쓰인다.
POST /api/coding/problems/{id}/submissions { "language": "Java", "code": "...", "explanation": "..." }
```

- **문제 원문은 저장하지 않습니다.** 출처, URL, 제목, 난이도, 태그와 **내가 직접 쓴 요약**만 저장하고 원문은 원래 사이트에서 확인합니다. 출처 URL은 `http(s)`만 받습니다.
- **특강**은 설계서 16장의 17단계(문제 이해 → … → 핵심 복습)이고, **단계 번호와 제목은 코드가 고정**하며 모델은 본문만 씁니다. "왜 이 코드를 쓰는가"를 조건 → 사고 → 알고리즘 → 자료구조 → 코드 순서로 설명합니다.
  요약만으로 알 수 없는 것(입력 형식, 제한 등)은 모델이 지어내지 않고 **"가정"으로 명시**하며, 화면 맨 위에 가정 목록이 나옵니다. 실제 문제와 다르면 요약을 보강해 다시 만드세요.
- **풀이 비교**는 8개 항목(알고리즘, 자료구조, 시간복잡도, 공간복잡도, 코드 구조, 가독성, 예외 처리, 문제 이해)을 권장 접근과 대조하고 차이의 이유를 설명합니다. 항목 이름과 순서는 코드가 검증합니다.
  **코드를 실행해 채점하지 않습니다.** 읽고 분석한 의견이며 정답 여부는 원래 사이트에서 확인하세요. 제출 코드와 설명은 분석 대상일 뿐 지시문이 아니도록 프롬프트에서 막습니다.
- 특강은 분량이 커서 모델에 따라 1~3분 걸릴 수 있습니다(실측: Claude Code 약 75초, 풀이 비교 약 30초). 제한 시간(기본 180초)이 모자라면 `CLAUDE_CODE_TIMEOUTSECONDS`를 늘리세요.
- 문제 요약, 코드, 설명은 선택한 LLM으로 전달됩니다(Claude Code/API는 Anthropic, Ollama는 내 PC 안).

## 안드로이드 앱 (PWA)

대시보드는 PWA라서 폰에서 **홈 화면에 설치**하면 앱처럼 열립니다(`manifest.webmanifest`, 아이콘, 서비스 워커 포함, 별도 빌드 도구 없음).
Spring을 서버에 두고 폰이 그 주소로 접속하는 구성입니다.

1. 서버 `.env`에 설정:
   ```
   SERVER_ADDRESS=0.0.0.0        # 기본은 127.0.0.1(내 PC 전용)
   APP_TOKEN=<길고 무작위한 값>   # /api/** 에 Authorization: Bearer 를 요구 (비면 인증 없음 + 경고 로그)
   MCP_TOKEN=<다른 무작위 값>     # /mcp 도 열려 있다면
   ```
2. **HTTPS 필수**: Android Chrome은 HTTPS(또는 localhost)에서만 "앱 설치"를 제공합니다. Caddy/nginx 리버스 프록시나 Cloudflare Tunnel·Tailscale Funnel로 `https://내도메인`을 만드세요.
3. 폰 Chrome에서 그 주소를 열면 토큰을 한 번 묻습니다(이 기기에만 저장). 메뉴 → "홈 화면에 추가/앱 설치".
4. **서버에서는 Claude Code 로그인(CLI)을 쓸 수 없습니다**(로그인은 내 PC 전용). 서버에서는 `ANTHROPIC_API_KEY`(Claude API) 또는 서버에서 돌리는 Ollama를 설정에서 선택하세요.

APK가 꼭 필요하면 Capacitor로 이 `frontend/`를 감싸 Android Studio에서 빌드할 수 있습니다(현재 저장소에는 포함하지 않음).

### APK 빌드 (Capacitor)

`frontend/android/`에 Capacitor Android 프로젝트가 있습니다. 앱은 **서버에 배포된 HTTPS 대시보드를 그대로 엽니다**(`capacitor.config.ts`의 `server.url`). 그래서 화면을 고쳐도 APK를 다시 만들 필요가 없고, API 주소 설정도 필요 없습니다. 토큰 입력창(`APP_TOKEN`)도 그대로 동작합니다.

```bash
cd frontend
npm install
CAP_SERVER_URL=https://내도메인 npx cap sync android   # 서버 주소를 앱에 반영 (HTTPS만 허용)
npx cap open android                                    # Android Studio에서 ▶ 실행 또는 Build > Build APK(s)
```

- Android Studio(JDK 21, Android SDK)가 필요합니다. 서버 주소를 바꾸면 `cap sync`를 다시 하세요. 주소가 담긴 `android/app/src/main/assets/capacitor.config.json`은 Git에 올라가지 않습니다.
- `appId`는 `kr.doobae.personalai`, 아이콘은 Capacitor 기본값입니다(`android/app/src/main/res`에서 교체).
- 서버 설정(`SERVER_ADDRESS`, `APP_TOKEN`, HTTPS)은 위 PWA 섹션과 같습니다.

## 학습 통계

헤더의 "통계"(`#/stats`)에서 학습 현황을 한눈에 봅니다. LLM을 호출하지 않는 조회 전용 화면입니다.

```http
GET /api/stats   합계(목표·완료 단계·답변·코딩 문제·풀이 제출), 최근 14일 하루 활동, 풀이한 문제 분류별 개수, 사고 패턴 상태별 개수
```

- 하루 활동은 진단 답변 + 코딩 풀이 제출 수이고, 날짜는 서버 시간대 기준입니다. 활동이 없는 날도 0으로 표시됩니다.
- **진단 정답률 추이**: 목표별로 채점된 진단마다 점이 하나씩 찍힙니다(진단을 다시 받을수록 변화가 보입니다). **보강이 필요한 영역**은 목표별 가장 최근 진단에서 점수가 낮은 5개 영역입니다. 읽을 수 없는 채점 결과는 건너뛰고 서버 로그에 남깁니다.
- **복습할 Step**: 확인 문제에서 합격선에 못 미쳐 "보충 필요"가 된 Step을 마지막 점수가 낮은 순으로 보여 줍니다(`reviews`). 같은 목록이 Discord 일일 알림에도 "복습 필요"로 최대 5개까지 들어갑니다. 다시 학습해 합격하면 목록에서 사라집니다.

## Discord 알림

매일 정해진 시간에 Discord 채널로 "오늘의 학습"(목표별 진행률, 다음 할 단계, 오늘 활동)을 보냅니다.
**Incoming Webhook**만 쓰므로 봇 계정, 라이브러리, 공개 URL이 필요 없고, 서버가 Discord로 내보내기만 합니다.

1. Discord 채널 설정 → 연동 → 웹후크 → 새 웹후크 → URL 복사
2. `.env`에 설정 (URL은 비밀번호와 같습니다. 채팅·Git에 올리지 마세요):
   ```
   DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/...
   DISCORD_CRON=0 0 21 * * *     # 선택. 기본 매일 21:00
   DISCORD_ZONE=Asia/Seoul       # 선택. 기본 Asia/Seoul
   ```
3. 앱을 재시작하고 확인: `POST /api/notifications/discord/test` (지금 한 번 전송), `GET /api/notifications/discord` (활성 여부)

- `discord.com`/`discordapp.com`의 웹후크 주소가 아니면 알림을 끕니다. 전송 실패 메시지에는 URL이 들어가지 않습니다.
- 본문에 `@everyone` 등이 섞여도 아무도 호출하지 않도록 멘션을 막아 둡니다.
- 서버가 꺼져 있던 시간의 알림은 보내지 않습니다(밀린 알림 없음).
- 명령어(`/next` 등)로 Discord에서 질의하는 기능은 아직 없습니다(봇 계정 필요).

## CI와 비밀 값 검사

`.github/workflows/ci.yml`이 push/PR마다 백엔드 테스트, 프론트엔드 빌드, **gitleaks 비밀 값 검사**(전체 커밋 기록)를 돌립니다.
조직 소유 저장소에서 gitleaks 단계가 라이선스 오류를 내면 저장소 시크릿 `GITLEAKS_LICENSE`를 추가하거나 그 단계를 지우세요.
비밀 값(DB 비밀번호, `DISCORD_WEBHOOK_URL`, API 키, `APP_TOKEN`)은 `.env`에만 두고, 실수로 커밋·채팅에 노출했다면 **즉시 폐기하고 새로 발급**하세요(기록에서 지워도 이미 복사됐을 수 있습니다).

## Step 진행과 확인 문제

목표 화면의 커리큘럼에서 Step마다 **학습 시작 → 확인 문제 → 합격하면 완료**로 진행합니다.

```http
POST /api/learning/steps/{stepId}/start        시작 가능(AVAILABLE)·보충 필요(REVIEW_REQUIRED) → 학습 중(LEARNING). 갱신된 목표 상세를 돌려준다
POST /api/learning/steps/{stepId}/assessment   확인 문제(3~5개)를 만든다. 학습 중이어야 하고, 풀다 만 문제가 있으면 새로 만들지 않고 그대로 돌려준다
POST /api/learning/assessments/{id}/answers    진단과 같은 제출 API. Step 문제면 응답에 stepOutcome(합격 여부, 합격선, Step 새 상태)이 들어 있다
```

- **합격선 70점**(난이도 가중 정답률, `StepProgressService.PASS_SCORE`). 합격하면 Step이 `COMPLETED`가 되고 **다음 Step이 열립니다**. 못 미치면 `REVIEW_REQUIRED`(보충 필요)가 되어 다시 학습한 뒤 새 문제로 도전합니다(횟수 제한 없음).
- 문제는 그 Step의 목표만 묻고, 채점은 진단과 같은 코드 경로(LLM은 문항별 점수만, 점수 계산은 코드)를 씁니다. 단, **Step 문제는 쉬워서 목표의 현재 수준(`currentLevel`)을 바꾸지 않습니다.**
- Step 답변도 사고 패턴 분석의 대상이 됩니다. 상태 전이는 `StepProgressService` 한 곳에 모여 있어 잘못된 상태에서 호출하면 `409`입니다.
- 실측(Claude Code): 문제 생성 약 30초, 채점 약 10~13초.

### Step 학습 자료 (Tutor Agent)

열린 Step(잠김 제외)마다 "학습 자료 보기"로 **개념 → 예제 → 직접 구현 → 완료 체크** 자료를 봅니다. 확인 문제는 이 자료를 공부한 뒤 푸는 순서입니다.

```http
POST /api/learning/steps/{stepId}/lesson[?refresh=true]   저장된 자료가 있으면 그대로, 없거나 refresh=true면 AI가 새로 만든다. 잠긴 Step은 409
```

- 자료는 Step의 `detail` JSON(`lesson` 키)에 저장되어 **다시 열 때는 LLM을 부르지 않습니다**(`refresh`로만 새로 만듦, 다시 만들면 이전 자료는 사라짐).
- **개인화**: 진단에서 관찰된 약점과 사고 패턴 가설(기각된 것 제외, 최대 3개)을 참고 자료로 넘겨 약한 부분은 더 풀어 쓰고 설명 방식에 조용히 반영합니다. 이 값들은 가설이라 프롬프트에서 지시문으로 취급하지 않게 막았고, 학습자를 평가하거나 단정하는 말은 쓰지 않게 했습니다.
- 모델 응답은 개념 2~5개, 예제 1~3개(코드 3000자 이하), 구현 과제, 완료 체크 3~6개를 갖춰야 통과하며 아니면 1회 재시도합니다.
- 완료 체크 체크박스는 이 기기에만 저장되고 합격 여부와는 무관한 자가 점검용입니다(합격은 확인 문제로만 판정). 실측(Claude Code): 자료 생성 약 38초.

## DB 최소 권한 계정 (서버에 올릴 때 권장)

기본 설정은 `DB_USERNAME` 하나로 스키마 변경(Flyway)과 앱 실행을 모두 합니다. 서버에 공개하는 앱이라면 **앱 계정은 데이터만 읽고 쓰게** 낮추세요.
앱이 뚫리거나 SQL 주입이 생겨도 테이블 삭제·변경은 막힙니다(앱 코드는 `DELETE`를 쓰지 않습니다).

1. 소유자 계정(지금 쓰는 계정)으로 한 번 실행 — 비밀번호는 파일에 쓰지 말고 변수로 넘깁니다:
   ```bash
   psql "<소유자 접속 문자열>" -v app_password='<새 비밀번호>' -f scripts/db/create-app-role.sql
   ```
   `personal_ai_app` 계정이 만들어지고 `personal_ai` 스키마에 `SELECT/INSERT/UPDATE`만 갖습니다. 이후 마이그레이션(V7…)이 만드는 새 테이블에도 같은 권한이 자동으로 붙습니다.
2. `.env`를 바꿉니다:
   ```
   DB_USERNAME=personal_ai_app            # 앱 실행 계정 (Supabase pooler면 personal_ai_app.<프로젝트ref>)
   DB_PASSWORD=<새 비밀번호>
   DB_MIGRATION_USERNAME=<소유자 계정>      # Flyway만 사용. 비우면 DB_USERNAME을 그대로 씀
   DB_MIGRATION_PASSWORD=<소유자 비밀번호>
   ```
   소유자 비밀번호는 서버 `.env`에만 두고, 로컬 개발용과 서버용 계정을 나누면 더 안전합니다.
- 확인된 것: 앱 계정으로 조회·저장은 되고, `DELETE`/`DROP`/`ALTER`/`CREATE`는 `permission denied`입니다(로컬 Postgres 16).
- 앱 계정만 설정하고 `DB_MIGRATION_*`를 빼면 새 마이그레이션이 있는 버전으로 올릴 때 Flyway가 권한 오류로 멈춥니다.

## 오늘의 학습·오늘의 특강

메인 화면(목표 목록) 맨 위에 설계서 20장의 두 영역이 나옵니다. LLM을 호출하지 않고 이미 있는 데이터로 계산합니다.

```http
GET /api/today   할 일 체크리스트(todos)와 오늘의 특강(lecture, 후보가 없으면 null)
```

- **오늘의 학습**: 오늘 합격한 Step(체크됨) → 목표마다 지금 할 일 하나(진단 만들기·답변 제출·커리큘럼 만들기, 또는 열린 Step의 학습 시작/이어서/확인 문제) → 복습할 Step(보충 필요) 순서입니다. 각 항목을 누르면 해당 목표 화면으로 갑니다. "오늘"은 서버 시간대 기준입니다.
- **오늘의 특강**: 특강이 있고 아직 풀이를 제출하지 않은 문제(오래된 것부터)를 먼저, 모두 풀었으면 가장 최근 특강을 보여 줍니다. 특강이 있는 문제가 없으면 카드가 나오지 않습니다.

## 학습 시간 기록

통계의 "학습 시간"은 **Step 학습 자료를 펼쳐 둔 시간 중 탭이 보이는 시간**입니다. 다른 탭으로 가거나 화면을 가리면 세지 않고, 자료를 접으면 멈춥니다.
확인 문제·코딩테스트 풀이 시간은 포함하지 않습니다(읽고 공부한 시간만).

```http
POST /api/learning/steps/{stepId}/study-time   { "seconds": 30 }   오늘 이 Step의 누적 초를 더한다(1~120초씩). 잠긴 Step은 409
```

- 화면이 30초마다(그리고 접거나 탭을 닫을 때) 보내고, 서버는 (Step, 날짜)당 한 행(`learning_session`, V7)에 합산합니다. 한 번에 120초, Step당 하루 8시간까지만 받아 클라이언트 오류나 조작으로 통계가 망가지지 않게 했습니다.
- 통계 화면에 총 학습 시간 타일이 생기고, 14일 활동 막대에 올리면 그날 학습 시간(분)이 보입니다. 분 단위는 반올림이라 1분 미만도 합산되어 반영됩니다.
- 실측: 34초 후 30초, 접을 때 남은 9초가 전송되어 39초가 저장됨(Chromium 헤드리스).

### 통계 확장 (설계서 21장)

```http
GET /api/stats/insights   확인 문제 성적, 영역 변화, 알고리즘별 풀이 일치율, 언어·분야별 학습량, 사고 패턴 변화
```

- **확인 문제 성적**: 평균 점수, 합격 횟수/시도 횟수, **재학습 횟수**(합격선에 못 미쳐 다시 학습하게 된 횟수).
- **좋아진 영역**: 진단을 2번 이상 받은 목표에서 첫 진단과 가장 최근 진단에 모두 있는 영역의 점수 변화(많이 오른 순, 최대 8개).
- **알고리즘별 풀이 일치율**: 코드를 실행해 채점하지 않으므로 "정답률"이라 부르지 않습니다. 문제의 알고리즘(쉼표/슬래시로 여러 개면 각각)별로 AI 풀이 비교에서 권장 접근과 같다(SAME)고 본 항목 ÷ (SAME + DIFFERENT)입니다. 판단 불가(UNKNOWN)는 뺍니다.
- **학습량**: 분야별 학습 시간(학습 자료를 열어 둔 시간), 언어별 코딩 풀이 수.
- **사고 패턴 변화**: 기각되지 않은 패턴의 관찰 근거 수, 개선 신호 수, 코드가 판단한 효과(개선되는 모습/엇갈림/개선 신호 없음).
- 이 화면은 위쪽 통계와 따로 불러와서, 추가 통계가 실패해도 기존 통계는 그대로 보입니다. LLM을 호출하지 않습니다.

## 캘린더 구독 (ICS)

학습 계획을 캘린더 앱(Google 캘린더, Apple 캘린더, Outlook 등)에서 구독합니다. OAuth나 외부 서비스 연결 없이 앱이 ICS 주소를 제공하고, 캘린더 앱이 주기적으로(보통 몇 시간~하루) 가져갑니다.

1. `.env`에 비밀 토큰을 정합니다(16자 이상, 무작위). **이 주소를 아는 사람은 누구나 일정을 볼 수 있으니 주소 자체가 비밀입니다.**
   ```
   CALENDAR_TOKEN=<길고 무작위한 값>
   CALENDAR_START=21:00      # 선택. 매일 학습 일정 시작 시각, 기본 21:00
   CALENDAR_ZONE=Asia/Seoul  # 선택
   CALENDAR_DAYS=14          # 선택. 앞으로 보여 줄 일수(최대 60)
   ```
2. 캘린더 앱에서 "URL로 캘린더 추가/구독"에 `https://<서버 주소>/calendar/<토큰>/personal-ai.ics`를 넣습니다(앱을 서버에 올려 HTTPS로 열어야 외부 캘린더가 가져갑니다. 노트북 로컬 주소는 Google 캘린더가 가져가지 못합니다).
- **일정 내용**: 목표 마감일(종일, 진행률 100%면 제외), 그리고 **학습 계획** — 남은 Step을 하루에 하나씩 매일 같은 시각에 배치합니다(보충 필요 Step이 먼저, 여러 목표는 번갈아, 소요 시간은 30~120분으로 보정). 계획은 "제안"이라, Step을 끝내면 다음 갱신 때 일정이 앞으로 당겨집니다.
- 토큰이 없거나 16자 미만이면 피드가 꺼지고, 토큰이 틀리면 404를 돌려줍니다(`/api`의 `APP_TOKEN`과는 별개입니다. 캘린더 앱은 헤더를 보낼 수 없어서 주소에 넣었습니다). 시각은 UTC로 내보내서 캘린더 앱이 각자의 시간대로 보여 줍니다.

## Notion 학습 로그

하루 한 번 Notion 데이터베이스에 "학습 로그 YYYY-MM-DD" 페이지를 만들어 오늘의 학습 체크리스트(☑/□), 오늘 활동(답변·풀이·학습 시간), 목표별 진행 현황을 남깁니다.

1. Notion에서 [통합(integration)](https://www.notion.so/my-integrations)을 만들고 시크릿을 복사합니다.
2. 로그를 쌓을 데이터베이스를 만들고, 그 데이터베이스의 `…` 메뉴 → 연결(Connections)에서 위 통합을 추가합니다. 데이터베이스 주소의 32자리 ID를 복사합니다.
3. `.env`에 설정(시크릿은 비밀번호와 같습니다. 채팅·Git에 올리지 마세요):
   ```
   NOTION_TOKEN=<통합 시크릿>
   NOTION_DATABASE_ID=<데이터베이스 ID>
   NOTION_TITLE_PROPERTY=Name    # 데이터베이스의 제목 속성 이름. 한글 Notion이면 보통 "이름"
   NOTION_CRON=0 10 21 * * *     # 선택. 기본 매일 21:10
   ```
4. 앱을 재시작하고 확인: `POST /api/notifications/notion/test`(지금 페이지를 새로 만들고 URL을 돌려줌), `GET /api/notifications/notion`(활성 여부).
- 자동 기록은 **하루에 한 번만** 만듭니다(마지막으로 남긴 날짜를 DB에 기록). 실패하면 날짜를 기록하지 않아 다음 실행 때 다시 시도합니다. 수동 테스트는 항상 새 페이지를 만듭니다.
- 토큰과 데이터베이스 ID 형식이 틀리면 시작을 막지 않고 기능만 끕니다. 오류 메시지에는 HTTP 상태와 Notion이 알려 준 짧은 사유(예: "Name is not a property that exists.")만 담고 토큰은 넣지 않습니다.
- 서버가 꺼져 있던 날의 로그는 만들지 않습니다.

