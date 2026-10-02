import type { Level, NextAction, StepStatus } from './api'

export const LEVEL_LABEL: Record<Level, string> = {
  BEGINNER: '입문', INTERMEDIATE: '중급', ADVANCED: '고급',
}

export const STATUS_LABEL: Record<StepStatus, string> = {
  LOCKED: '잠김', AVAILABLE: '시작 가능', LEARNING: '학습 중', PRACTICE: '실습 중',
  WAITING_ASSESSMENT: '평가 대기', ASSESSMENT: '평가 중', PASSED: '통과',
  REVIEW_REQUIRED: '보충 필요', COMPLETED: '완료',
}

export const NEXT_TITLE: Record<NextAction, string> = {
  DIAGNOSTIC_NEEDED: '다음: 현재 수준 진단',
  ANSWERS_NEEDED: '다음: 진단 답변 제출',
  CURRICULUM_NEEDED: '다음: 커리큘럼 생성',
  LEARNING: '학습 진행 중',
}

export const formatMinutes = (m: number) =>
  m >= 60 ? `${Math.floor(m / 60)}시간${m % 60 ? ` ${m % 60}분` : ''}` : `${m}분`

/** 점수 구간: 60 미만은 보강 필요, 80 이상은 충분 (커리큘럼 생성 기준과 같다). */
export const scoreTone = (score: number) => (score < 60 ? 'weak' : score >= 80 ? 'strong' : 'mid')

export const TYPE_LABEL: Record<string, string> = {
  CONCEPT: '개념', SYNTAX: '문법', UNDERSTANDING: '이해', APPLICATION: '응용',
  PROBLEM_SOLVING: '문제 해결', CODE_QUALITY: '코드 품질', DEBUGGING: '디버깅',
}

export const difficultyDots = (d: number) => '●'.repeat(d) + '○'.repeat(Math.max(0, 5 - d))

export const PROVIDER_LABEL = { OLLAMA: 'Ollama (로컬)', CLAUDE: 'Claude (API)', CLAUDE_CODE: 'Claude Code (로그인)' } as const
export const CLAUDE_MODEL_SUGGESTIONS = ['claude-sonnet-5-5', 'claude-opus-5-5', 'claude-haiku-4-5-20251001']
export const CLAUDE_CODE_MODEL_SUGGESTIONS = ['sonnet', 'opus', 'haiku']
export const modelName = (m: string | null) => m ?? '기본 모델'

export const PATTERN_STATUS_LABEL = { HYPOTHESIS: '가설', SUPPORTED: '반복 관찰됨', DISMISSED: '기각됨' } as const
