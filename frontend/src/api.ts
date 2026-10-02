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

export interface GoalDetail {
  goal: GoalSummary
  nextAction: NextAction
  pendingAssessmentId: number | null
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

async function get<T>(path: string): Promise<T> {
  const res = await fetch(path)
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.message || `요청 실패 (${res.status})`)
  }
  return res.json()
}

export const fetchGoals = () => get<GoalSummary[]>('/api/learning/goals')
export const fetchGoal = (id: number) => get<GoalDetail>(`/api/learning/goals/${id}`)
