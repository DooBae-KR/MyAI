export type Level = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'

export type StepStatus =
  | 'LOCKED' | 'AVAILABLE' | 'LEARNING' | 'PRACTICE' | 'WAITING_ASSESSMENT'
  | 'ASSESSMENT' | 'PASSED' | 'REVIEW_REQUIRED' | 'COMPLETED'

export type NextAction = 'DIAGNOSTIC_NEEDED' | 'ANSWERS_NEEDED' | 'CURRICULUM_NEEDED' | 'LEARNING'

export interface GoalSummary {
  goalId: number
  subject: string
  goalText: string
  targetLevel: Level | null
  currentLevel: Level | null
  deadline: string | null
  totalSteps: number
  completedSteps: number
  progressPercent: number
}

export interface Step {
  id: number
  seq: number
  title: string
  objective: string
  difficulty: number
  estimatedMinutes: number
  practiceTasks: string[]
  status: StepStatus
}

export interface QuizQuestion {
  id: number
  area: string
  type: string
  difficulty: number
  question: string
}

export interface Quiz {
  promptVersion: string
  questions: QuizQuestion[]
}

export interface GradingResult {
  correctness: number
  level: Level
  areaScores: { area: string; score: number }[]
  strengths: string[]
  weaknesses: string[]
  questionResults: { questionId: number; score: number; feedback: string }[]
}

export interface GoalDetail {
  goal: GoalSummary
  nextAction: NextAction
  pendingAssessmentId: number | null
  pendingQuiz: Quiz | null
  diagnostic: {
    assessmentId: number
    correctness: number
    level: Level
    areaScores: { area: string; score: number }[]
    strengths: string[]
    weaknesses: string[]
  } | null
  steps: Step[]
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init)
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.message || `요청 실패 (${res.status})`)
  }
  return res.json()
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, {
    method: 'POST',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

export const fetchGoals = () => request<GoalSummary[]>('/api/learning/goals')
export const fetchGoal = (id: number) => request<GoalDetail>(`/api/learning/goals/${id}`)

export interface NewGoal {
  subject: string
  goal: string
  targetLevel?: Level
  deadline?: string
}
export const createGoal = (goal: NewGoal) =>
  post<{ subjectId: number; goalId: number }>('/api/learning/subjects', goal)
export const createDiagnostic = (goalId: number) => post<unknown>(`/api/learning/goals/${goalId}/diagnostic`)
export const submitAnswers = (assessmentId: number, answers: { questionId: number; answer: string }[]) =>
  post<{ result: GradingResult }>(`/api/learning/assessments/${assessmentId}/answers`, { answers })
export const createCurriculum = (goalId: number) => post<unknown>(`/api/learning/goals/${goalId}/curriculum`)

export type Provider = 'OLLAMA' | 'CLAUDE'

export interface LlmSettings {
  provider: Provider
  ollamaModel: string | null
  claudeModel: string | null
  activeModel: string
  claudeAvailable: boolean
  defaults: { ollamaModel: string; claudeModel: string }
}

export interface LlmTestResult {
  ok: boolean
  provider: Provider
  model: string
  elapsedMs: number
  reply: string | null
  message: string | null
}

export const fetchLlmSettings = () => request<LlmSettings>('/api/settings/llm')
export const updateLlmSettings = (body: { provider: Provider; ollamaModel: string; claudeModel: string }) =>
  request<LlmSettings>('/api/settings/llm', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
export const testLlm = () => post<LlmTestResult>('/api/settings/llm/test')
