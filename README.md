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
