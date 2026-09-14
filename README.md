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
