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
`{ "assessmentId", "goalId", "quiz": { "promptVersion", "questions": [...] } }`입니다.
목표가 없으면 `404`, 모델 응답이 형식을 두 번 어기면 `502`, LLM에 연결할 수 없으면 `503`입니다.
프롬프트는 `ai-agent/src/main/resources/prompts/`에 있고 `promptVersion`이 결과와 함께 저장됩니다.
