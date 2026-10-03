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

// 서버가 APP_TOKEN으로 보호되면 401이 오고, 토큰을 한 번 물어 이 기기에만 저장한다.
const TOKEN_KEY = 'personal-ai-token'
const savedToken = () => { try { return localStorage.getItem(TOKEN_KEY) } catch { return null } }

async function send(path: string, init?: RequestInit) {
  const token = savedToken()
  const headers = token ? { ...init?.headers, Authorization: `Bearer ${token}` } : init?.headers
  return fetch(path, { ...init, headers })
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await send(path, init)
  if (res.status === 401) {
    const entered = window.prompt('서버 접속 토큰(APP_TOKEN)을 입력하세요')
    if (entered) {
      try { localStorage.setItem(TOKEN_KEY, entered.trim()) } catch { /* 저장 불가 시 이번 요청만 */ }
      res = await send(path, init)
    }
  }
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
export const startStep = (stepId: number) => post<GoalDetail>(`/api/learning/steps/${stepId}/start`)
export const completeStep = (stepId: number) => post<GoalDetail>(`/api/learning/steps/${stepId}/complete`)
export const createCurriculum = (goalId: number) => post<unknown>(`/api/learning/goals/${goalId}/curriculum`)

export type Provider = 'OLLAMA' | 'CLAUDE' | 'CLAUDE_CODE'

export interface LlmSettings {
  provider: Provider
  ollamaModel: string | null
  claudeModel: string | null
  claudeCodeModel: string | null
  activeModel: string | null // Claude Code는 모델을 지정하지 않으면 null(= CLI 기본 모델)
  claudeAvailable: boolean
  claudeCodeAvailable: boolean
  claudeCodeCommand: string | null // 찾은 실행 파일
  claudeCodeProblem: string | null // 못 찾았을 때의 이유와 시도한 위치
  defaults: { ollamaModel: string; claudeModel: string }
}

export interface LlmTestResult {
  ok: boolean
  provider: Provider
  model: string | null
  elapsedMs: number
  reply: string | null
  message: string | null
}

export const fetchLlmSettings = () => request<LlmSettings>('/api/settings/llm')
export const updateLlmSettings = (body: { provider: Provider; ollamaModel: string; claudeModel: string; claudeCodeModel: string }) =>
  request<LlmSettings>('/api/settings/llm', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
export const testLlm = () => post<LlmTestResult>('/api/settings/llm/test')
export const refreshClaudeCode = () => post<LlmSettings>('/api/settings/llm/claude-code/refresh')

export type PatternStatus = 'HYPOTHESIS' | 'SUPPORTED' | 'DISMISSED'

export interface PatternView {
  id: number
  name: string
  description: string | null
  status: PatternStatus
  confidence: number // 0.00~1.00
  evidenceCount: number
  firstObservedAt: string
  lastObservedAt: string
  improvementStrategy: string | null
  evidence: { source: 'ANSWER' | 'SUBMISSION'; itemId: number; label: string; quote: string; note: string | null }[]
}

export interface PatternAnalysis {
  analyzedAnswers: number
  analyzedSubmissions: number
  newPatterns: number
  updatedPatterns: number
  message: string | null
}

export interface Stats {
  totals: { goals: number; stepsTotal: number; stepsCompleted: number; answers: number; submissions: number; problems: number }
  activity: { date: string; answers: number; submissions: number }[]
  codingByCategory: { name: string; count: number }[]
  patternsByStatus: { name: PatternStatus; count: number }[]
  diagnosticTrends: { goalId: number; subject: string; points: { date: string; correctness: number }[] }[]
  weakAreas: { subject: string; area: string; score: number }[]
}

export const fetchStats = () => request<Stats>('/api/stats')

export const fetchPatterns = () => request<PatternView[]>('/api/learning/patterns')
export const analyzePatterns = () => post<PatternAnalysis>('/api/learning/patterns/analyze')
export const updatePattern = (id: number, status: 'DISMISSED' | 'HYPOTHESIS') =>
  request<PatternView>(`/api/learning/patterns/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  })

export interface CodingProblemSummary {
  id: number
  title: string
  source: string | null
  difficulty: string | null
  category: string | null
  algorithm: string | null
  dataStructure: string | null
  language: string | null
  estimatedTime: number | null
  hasLecture: boolean
  submissionCount: number
}

export interface LectureSection { no: number; title: string; body: string }
export interface Lecture { promptVersion: string; assumptions: string[]; sections: LectureSection[] }

export interface Comparison { item: string; mine: string; recommended: string; status: 'SAME' | 'DIFFERENT' | 'UNKNOWN'; reason: string }
export interface Review {
  promptVersion: string
  userApproach: string
  recommendedApproach: string
  comparisons: Comparison[]
  mistakes: string[]
  nextSteps: string[]
  similarProblems: string[]
  summary: string
}
export interface Submission { id: number; language: string; code: string; explanation: string | null; review: Review | null; createdAt: string }

export interface CodingProblemDetail {
  id: number
  title: string
  source: string | null
  sourceUrl: string | null
  externalId: string | null
  company: string | null
  difficulty: string | null
  category: string | null
  algorithm: string | null
  dataStructure: string | null
  language: string | null
  summary: string | null
  estimatedTime: number | null
  lecture: Lecture | null
  submissions: Submission[]
}

export interface NewCodingProblem {
  title: string
  source?: string
  sourceUrl?: string
  difficulty?: string
  category?: string
  algorithm?: string
  dataStructure?: string
  language?: string
  summary?: string
  estimatedTime?: number
}

export const fetchProblems = () => request<CodingProblemSummary[]>('/api/coding/problems')
export const fetchProblem = (id: number) => request<CodingProblemDetail>(`/api/coding/problems/${id}`)
export const createProblem = (p: NewCodingProblem) => post<CodingProblemDetail>('/api/coding/problems', p)
export const createLecture = (id: number) => post<Lecture>(`/api/coding/problems/${id}/lecture`)
export const submitSolution = (id: number, body: { language: string; code: string; explanation: string }) =>
  post<Submission>(`/api/coding/problems/${id}/submissions`, body)
