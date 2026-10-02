import type { Level, NextAction, StepStatus } from './api'

export const LEVEL_LABEL: Record<Level, string> = {
  BEGINNER: '입문', INTERMEDIATE: '중급', ADVANCED: '고급',
}

export const STATUS_LABEL: Record<StepStatus, string> = {
  LOCKED: '잠김', AVAILABLE: '시작 가능', LEARNING: '학습 중', PRACTICE: '실습 중',
  WAITING_ASSESSMENT: '평가 대기', ASSESSMENT: '평가 중', PASSED: '통과',
  REVIEW_REQUIRED: '보충 필요', COMPLETED: '완료',
}

export const NEXT_ACTION: Record<NextAction, { title: string; hint: (id: number, pending: number | null) => string }> = {
  DIAGNOSTIC_NEEDED: {
    title: '다음: 현재 수준 진단',
    hint: (id) => `POST /api/learning/goals/${id}/diagnostic 로 진단 문제를 생성하세요.`,
  },
  ANSWERS_NEEDED: {
    title: '다음: 진단 답변 제출',
    hint: (_id, pending) => `POST /api/learning/assessments/${pending}/answers 로 답변을 제출하세요.`,
  },
  CURRICULUM_NEEDED: {
    title: '다음: 커리큘럼 생성',
    hint: (id) => `POST /api/learning/goals/${id}/curriculum 으로 개인 커리큘럼을 만드세요.`,
  },
  LEARNING: { title: '학습 진행 중', hint: () => '시작 가능한 Step부터 학습하세요.' },
}

export const formatMinutes = (m: number) =>
  m >= 60 ? `${Math.floor(m / 60)}시간${m % 60 ? ` ${m % 60}분` : ''}` : `${m}분`

/** 점수 구간: 60 미만은 보강 필요, 80 이상은 충분 (커리큘럼 생성 기준과 같다). */
export const scoreTone = (score: number) => (score < 60 ? 'weak' : score >= 80 ? 'strong' : 'mid')
