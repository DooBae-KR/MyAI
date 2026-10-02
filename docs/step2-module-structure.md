# Step 2. 모듈 구조 설계

## 1. 현재 구조 (Step 1 분석)

```text
ai-core   AiModel / AiRequest / AiResponse            (순수 Java, 의존성 없음)
ai-llm    OllamaAiModel, ClaudeAiModel (ai.provider로 선택)
ai-api    PersonalAiApplication, AiController, AiService, McpController
```

의존: `ai-api → ai-llm → ai-core`, `ai-api → ai-core`

## 2. 목표 구조

기획서의 7개 모듈 중 **지금 필요한 것만** 만들고, 나머지는 해당 Phase에서 추가한다.
빈 모듈을 미리 만들지 않는다 (YAGNI).

```text
ai-core      계약 + 순수 도메인 타입 (enum, 값 객체). Spring/JPA 의존 없음      [있음]
ai-llm       LLM Adapter (Ollama, Claude). 외부 API 호출은 여기만             [있음]
ai-data      JPA 엔티티, Repository, Flyway 마이그레이션 (PostgreSQL/Supabase)              [Step 3 신규]
ai-agent     Agent + Prompt 로딩 + JSON 응답 검증. DB를 모른다                 [Step 5 신규]
ai-api       REST/MCP, 서비스(Agent 호출 → 검증 → ai-data 저장 조율)           [있음]
frontend/    React (Vite + TypeScript)                                        [Step 7 신규]

ai-analysis  사고 패턴 분석                                                    [Phase 6]
ai-memory    장기 기억. 당분간 ai-data의 조회로 충분 → Phase 6에서 분리 여부 결정
```

## 3. 의존 방향

```text
ai-api ──► ai-agent ──► ai-core ◄── ai-llm
   │                       ▲
   └──────► ai-data ───────┘
```

- `ai-agent`는 `AiModel` 인터페이스만 쓴다. Ollama/Claude 구현을 모른다.
- `ai-agent`와 `ai-data`는 서로 모른다. 연결은 `ai-api`의 서비스가 한다.
  (기획서 원칙: LLM 호출과 비즈니스 로직 분리, DB 저장과 LLM 응답 분리)
- 순환 의존 없음. `ai-core`는 아무것도 의존하지 않는다.

## 4. 패키지 규칙

`com.personal.ai.<모듈>.<기능>` (기존 `com.personal.ai.llm.ollama` 방식 유지)

```text
ai-core    com.personal.ai.core.model      AiModel ... (기존)
           com.personal.ai.core.learning   StepStatus, Level 등 enum
ai-data    com.personal.ai.data.learning   LearningSubject ... + Repository
           src/main/resources/db/migration V1__*.sql
ai-agent   com.personal.ai.agent.<agent명>
           src/main/resources/prompts/*.md
ai-api     com.personal.ai.api.learning    Controller / Service / DTO
```

## 5. Step 3 도메인 (ai-data)

| 엔티티 | 핵심 필드 | 관계 |
|---|---|---|
| LearningSubject | name, description | 선행 분야 N:M (자기 참조) |
| LearningGoal | subject, goalText, targetLevel, currentLevel, deadline | Subject 1:N |
| LearningStep | goal, seq, title, objective, difficulty, status | Goal 1:N |
| Assessment | goal 또는 step, type, questions(JSON) | Step 1:N |
| LearningAnswer | assessment, answerText, scores(JSON) | Assessment 1:N |

- `status`는 기획서 26장의 9개 상태 enum (`StepStatus`, ai-core).
- 점수는 컬럼을 늘리지 않고 JSON 한 컬럼에 둔다 (평가 항목이 바뀌어도 스키마 고정).
- Subject는 enum이 아니라 테이블이다 (임의 분야 확장).

## 6. DB: Supabase(PostgreSQL) 규칙

- 앱은 JDBC로 직접 접속한다. 테이블은 **전용 스키마 `personal_ai`** 에 둔다. Supabase API(PostgREST)는 기본적으로 `public`만 노출하므로 학습 기록이 API로 열리지 않는다.
- ID는 `BIGINT GENERATED ALWAYS AS IDENTITY`, 구조화 응답은 `JSONB`, 긴 텍스트는 `TEXT`
- 스키마는 Flyway만으로 변경 (`ddl-auto=none`). 매핑은 Testcontainers(`postgres:16-alpine`) 통합 테스트가 검증한다
- 접속 정보는 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로만 주입하고 Git에 올리지 않는다
- Supabase pooler를 Transaction 모드(6543)로 쓰면 JDBC URL에 `prepareThreshold=0`이 필요하다. Session 모드(5432)는 그대로 쓴다

## 7. Agent / Prompt 규칙 (Step 5 대비)

- Prompt는 `ai-agent/src/main/resources/prompts/<agent>[-<작업>].md`, 상단에 `promptVersion` 기재 (예: `evaluator-diagnostic.md`). 버전은 저장되는 JSON에 함께 기록한다
- Agent는 JSON Schema에 맞는 응답을 요구하고, 파싱·검증 실패 시 1회 재시도 후 예외
- 검증 통과한 DTO만 서비스가 DB에 저장한다 (LLM 응답 원문을 그대로 저장하지 않음)

## 8. 기존 코드 정리 (별도 작업, 이번 Step 범위 아님)

1. `ai-llm/build/`가 Git에 커밋되어 있다. `.gitignore`의 `#build/` 주석을 풀고 추적 해제 필요.
2. `ai-llm/build.gradle`의 `spring-boot-starter-web:3.5.5` 등 버전이 하드코딩되어 있다.
   Spring Boot BOM으로 통일하면 버전 수정이 한 곳으로 줄어든다.
3. `AiRequest`에 `temperature`가 있으나 `OllamaAiModel`은 무시한다.

## 9. 결정 필요 사항 (기본값으로 진행 가능)

| 항목 | 기본값 | 이유 |
|---|---|---|
| User 테이블 | **만들지 않는다** | 단일 사용자 개인 비서. 다중 사용자가 필요해질 때 마이그레이션으로 추가 |
| ai-memory 모듈 | **Phase 6까지 미룬다** | 지금은 ai-data 조회로 충분 |
| JPA vs JdbcTemplate | **Spring Data JPA** | 엔티티 관계가 많고 PostgreSQL 방언 지원이 성숙 |
