import { useState } from 'react'
import { fetchGoal, fetchGoals, fetchLlmSettings, openStepAssessment, startStep, type GoalDetail, type GoalSummary, type Quiz } from './api'
import { useAction, useHashRoute, useLoad } from './hooks'
import { LEVEL_LABEL, PROVIDER_LABEL, STATUS_LABEL, difficultyDots, formatMinutes, modelName, scoreTone } from './labels'
import { NewGoalForm } from './NewGoalForm'
import { AnswerForm, NextActionPanel } from './NextActionPanel'
import { CodingPage, ProblemPage } from './CodingPage'
import { PatternsPage } from './PatternsPage'
import { StatsPage } from './StatsPage'
import { SettingsPage } from './SettingsPage'

export default function App() {
  const route = useHashRoute()
  const llm = useLoad(fetchLlmSettings, 'llm')
  return (
    <>
      <header className="top">
        <a href="#/" className="brand">Personal AI</a>
        <span className="muted">학습 Dashboard</span>
        <nav className="spacer">
          <a href="#/coding" className="nav-link">코딩테스트</a>
          <a href="#/stats" className="nav-link">통계</a>
          <a href="#/patterns" className="nav-link">사고 패턴</a>
          <a href="#/settings" className="llm-badge" aria-label="LLM 설정">
            {llm.data ? `${PROVIDER_LABEL[llm.data.provider]} · ${modelName(llm.data.activeModel)}` : '설정'}
          </a>
        </nav>
      </header>
      <main>
        {route.page === 'settings' && <SettingsPage onSaved={llm.reload} />}
        {route.page === 'stats' && <StatsPage />}
        {route.page === 'patterns' && <PatternsPage />}
        {route.page === 'coding' && <CodingPage />}
        {route.page === 'problem' && <ProblemPage problemId={route.problemId} />}
        {route.page === 'goal' && <GoalPage goalId={route.goalId} />}
        {route.page === 'list' && <GoalList />}
      </main>
    </>
  )
}

function Level({ value }: { value: keyof typeof LEVEL_LABEL | null }) {
  return <>{value ? LEVEL_LABEL[value] : '미정'}</>
}

function Progress({ percent, label }: { percent: number; label: string }) {
  return (
    <div className="progress" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100} aria-label={label}>
      <div className="progress-fill" style={{ width: `${percent}%` }} />
    </div>
  )
}

function GoalList() {
  const { data, error } = useLoad<GoalSummary[]>(fetchGoals, 'list')
  if (error) return <p className="error" role="alert">목록을 불러오지 못했습니다: {error}</p>
  if (!data) return <p className="muted">불러오는 중…</p>
  return (
    <>
      <h1>내 학습 목표</h1>
      <details className="new-goal" open={data.length === 0}>
        <summary>{data.length === 0 ? '첫 학습 목표를 등록하세요' : '+ 새 목표 추가'}</summary>
        <NewGoalForm />
      </details>
      <ul className="cards">
        {data.map((g) => (
          <li key={g.goalId}>
            <a className="card" href={`#/goals/${g.goalId}`}>
              <div className="card-head">
                <h2>{g.subject}</h2>
                <span className="muted">
                  <Level value={g.currentLevel} /> → <Level value={g.targetLevel} />
                </span>
              </div>
              <p>{g.goalText}</p>
              <Progress percent={g.progressPercent} label={`${g.subject} 진행률`} />
              <div className="muted small">
                {g.totalSteps === 0 ? '커리큘럼 전' : `${g.completedSteps}/${g.totalSteps} Step 완료 · ${g.progressPercent}%`}
                {g.deadline && ` · 마감 ${g.deadline}`}
              </div>
            </a>
          </li>
        ))}
      </ul>
    </>
  )
}

function GoalPage({ goalId }: { goalId: number }) {
  const { data, error, reload } = useLoad<GoalDetail>(() => fetchGoal(goalId), goalId)
  return (
    <>
      <a href="#/" className="back">← 목록</a>
      {error && <p className="error" role="alert">목표를 불러오지 못했습니다: {error}</p>}
      {!data && !error && <p className="muted">불러오는 중…</p>}
      {data && <GoalView detail={data} onChanged={reload} />}
    </>
  )
}

function GoalView({ detail, onChanged }: { detail: GoalDetail; onChanged: () => void }) {
  const { goal, diagnostic, steps } = detail
  return (
    <>
      <h1>{goal.subject}</h1>
      <p>{goal.goalText}</p>
      <p className="muted">
        현재 <Level value={goal.currentLevel} /> → 목표 <Level value={goal.targetLevel} />
        {goal.deadline && ` · 마감 ${goal.deadline}`}
      </p>
      <Progress percent={goal.progressPercent} label="전체 진행률" />

      <NextActionPanel detail={detail} onChanged={onChanged} />

      {diagnostic && (
        <section aria-labelledby="diag">
          <h2 id="diag">진단 결과 <span className="muted small">정답률 {diagnostic.correctness}% · <Level value={diagnostic.level} /></span></h2>
          <ul className="areas">
            {diagnostic.areaScores.map((a) => (
              <li key={a.area}>
                <span>{a.area}</span>
                <div className={`bar ${scoreTone(a.score)}`} role="img" aria-label={`${a.area} ${a.score}점`}>
                  <div style={{ width: `${a.score}%` }} />
                </div>
                <span className="score">{a.score}</span>
              </li>
            ))}
          </ul>
          <div className="two">
            <div><h3>강점</h3><ul>{diagnostic.strengths.map((s) => <li key={s}>{s}</li>)}</ul></div>
            <div><h3>보완할 점</h3><ul>{diagnostic.weaknesses.map((s) => <li key={s}>{s}</li>)}</ul></div>
          </div>
          <p className="muted small">진단은 한 번의 답변에서 얻은 관찰입니다. 단정이 아니라 가설로 보고, 이후 평가에서 갱신됩니다.</p>
        </section>
      )}

      {steps.length > 0 && (
        <section aria-labelledby="steps">
          <h2 id="steps">커리큘럼 <span className="muted small">{steps.length} Step</span></h2>
          <ol className="steps">
            {steps.map((s) => (
              <li key={s.id} className={`step ${s.status === 'LOCKED' ? 'locked' : ''}`}>
                <div className="step-head">
                  <span className="seq">{s.seq}</span>
                  <h3>{s.title}</h3>
                  <span className={`badge ${s.status.toLowerCase()}`}>{STATUS_LABEL[s.status]}</span>
                </div>
                <p>{s.objective}</p>
                <div className="muted small">
                  난이도 {difficultyDots(s.difficulty)} · 약 {formatMinutes(s.estimatedMinutes)}
                </div>
                {s.practiceTasks.length > 0 && (
                  <ul className="tasks">{s.practiceTasks.map((t) => <li key={t}>{t}</li>)}</ul>
                )}
                <StepActions step={s} onChanged={onChanged} />
              </li>
            ))}
          </ol>
        </section>
      )}
    </>
  )
}

/** 학습 시작 → 확인 문제(70점 이상 합격하면 완료, 다음 Step 열림). 못 미치면 "보충 필요"가 되어 다시 학습한다. */
function StepActions({ step, onChanged }: { step: GoalDetail['steps'][number]; onChanged: () => void }) {
  const action = useAction()
  const [quiz, setQuiz] = useState<{ assessmentId: number; quiz: Quiz } | null>(null)

  if (quiz) {
    return <AnswerForm assessmentId={quiz.assessmentId} quiz={quiz.quiz} heading={`${step.title} 확인 문제`}
      onContinue={() => { setQuiz(null); onChanged() }} />
  }

  const next = step.status === 'AVAILABLE' || step.status === 'REVIEW_REQUIRED'
    ? { label: '학습 시작', run: async () => { await startStep(step.id); return null } }
    : step.status === 'LEARNING' || step.status === 'ASSESSMENT'
      ? { label: step.status === 'ASSESSMENT' ? '확인 문제 이어서 풀기' : '확인 문제 풀기',
          run: async () => openStepAssessment(step.id) }
      : null
  if (!next) return null

  async function click() {
    const res = await action.run(next!.run)
    if (res === undefined) return // 실패: action.error가 보인다
    if (res) setQuiz({ assessmentId: res.assessmentId, quiz: res.quiz })
    onChanged() // 상태 배지(학습 중 → 확인 중)를 갱신한다. 퀴즈 화면은 그대로 유지된다
  }

  return (
    <div className="actions">
      <button className="primary" disabled={action.pending} onClick={click}>{action.pending ? '처리 중…' : next.label}</button>
      {step.status === 'LEARNING' && <span className="muted small">공부를 마친 뒤 누르세요. 70점 이상이면 완료되고 다음 Step이 열립니다.</span>}
      {step.status === 'REVIEW_REQUIRED' && <span className="muted small">확인 문제에서 합격선에 못 미쳤습니다. 다시 학습하세요.</span>}
      {action.pending && step.status !== 'AVAILABLE' && step.status !== 'REVIEW_REQUIRED' && <span className="muted small" role="status">문제를 만드는 중… 모델에 따라 최대 1~2분 걸립니다</span>}
      {action.error && <span className="error" role="alert">{action.error}</span>}
    </div>
  )
}
