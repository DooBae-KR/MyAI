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
DB_URL=jdbc:postgresql://<pooler-호스트>:5432/postgres
DB_USERNAME=postgres.<프로젝트-ref>
DB_PASSWORD=<비밀번호>
```

**주의: `DB_URL`은 JDBC 형식입니다.** Supabase Connect 화면의 `postgresql://사용자:비밀번호@호스트:포트/postgres`를 그대로 넣으면
`'url' must start with "jdbc"` 오류가 납니다. 앞에 `jdbc:`를 붙이고 `사용자:비밀번호@`는 빼서, 사용자와 비밀번호는 각각 `DB_USERNAME`, `DB_PASSWORD`에 넣으세요.
주소와 사용자 이름은 한 쌍으로 맞아야 합니다(어긋나면 `Tenant or user not found`).

| 연결 방식 | DB_URL 호스트:포트 | DB_USERNAME |
|---|---|---|
| Session pooler (권장) | `aws-0-<region>.pooler.supabase.com:5432` | `postgres.<프로젝트-ref>` |
| Transaction pooler | `aws-0-<region>.pooler.supabase.com:6543` + URL 끝에 `?prepareThreshold=0` | `postgres.<프로젝트-ref>` |
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

대시보드 오른쪽 위의 모델 표시(또는 `#/settings`)에서 **Ollama(로컬)와 Claude(API)를 앱 재시작 없이** 바꿀 수 있습니다.
진단 문제 생성, 채점, 커리큘럼 생성, MCP가 모두 선택한 모델로 동작합니다. 모델 이름도 지정할 수 있고(비우면 기본값),
"연결 테스트"로 키와 모델이 동작하는지 바로 확인할 수 있습니다. 선택은 DB(`app_setting`)에 저장되어 재시작 후에도 유지됩니다.

```http
GET  /api/settings/llm         현재 선택, 기본 모델, Claude 사용 가능 여부
PUT  /api/settings/llm         { "provider": "CLAUDE", "ollamaModel": "", "claudeModel": "claude-opus-5-5" }
POST /api/settings/llm/test    현재 선택으로 짧은 호출을 보내 확인 (실패해도 200, ok=false와 사유)
```

- **API 키는 화면에서 입력하지 않습니다.** `.env`의 `ANTHROPIC_API_KEY`로만 설정합니다. 비었거나 자리표시자(공백이나 한글이 섞인 값)면 "키 없음"으로 보고 Claude 선택을 막습니다.
- `AI_PROVIDER`는 처음 시작할 때의 기본 선택이고, 저장된 선택이 있으면 그것이 우선합니다. 저장된 선택이 Claude인데 키가 사라졌다면 무시하고 Ollama로 시작합니다.
- Claude 호출은 비용이 듭니다. `/api` 엔드포인트는 인증이 없으니 외부에 노출하지 마세요.
