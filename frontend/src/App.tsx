import { useEffect, useState } from 'react'
import { fetchGoal, fetchGoals, type GoalDetail, type GoalSummary } from './api'
import { LEVEL_LABEL, NEXT_ACTION, STATUS_LABEL, formatMinutes, scoreTone } from './labels'

/** '#/' 는 목록, '#/goals/3' 는 상세. 화면이 둘뿐이라 라우터 라이브러리 없이 해시로 나눈다. */
function useHashRoute(): number | null {
  const parse = () => {
    const m = location.hash.match(/^#\/goals\/(\d+)$/)
    return m ? Number(m[1]) : null
  }
  const [goalId, setGoalId] = useState(parse)
  useEffect(() => {
    const onChange = () => setGoalId(parse())
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return goalId
}

/** 비동기 조회 상태(로딩/오류/데이터)를 한곳에서 다룬다. */
function useLoad<T>(load: () => Promise<T>, key: unknown) {
  const [state, setState] = useState<{ data?: T; error?: string }>({})
  useEffect(() => {
    let cancelled = false
    setState({})
    load().then(
      (data) => !cancelled && setState({ data }),
      (e: Error) => !cancelled && setState({ error: e.message }),
    )
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  return state
}

export default function App() {
  const goalId = useHashRoute()
  return (
    <>
      <header className="top">
        <a href="#/" className="brand">Personal AI</a>
        <span className="muted">학습 Dashboard</span>
      </header>
      <main>{goalId === null ? <GoalList /> : <GoalPage goalId={goalId} />}</main>
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
  if (data.length === 0) {
    return (
      <section className="empty">
        <h2>아직 학습 목표가 없습니다</h2>
        <p>다음 요청으로 첫 목표를 등록하세요.</p>
        <pre>{`POST /api/learning/subjects\n{ "subject": "Vue", "goal": "실무 수준까지 배우기" }`}</pre>
      </section>
    )
  }
  return (
    <>
      <h1>내 학습 목표</h1>
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
  const { data, error } = useLoad<GoalDetail>(() => fetchGoal(goalId), goalId)
  return (
    <>
      <a href="#/" className="back">← 목록</a>
      {error && <p className="error" role="alert">목표를 불러오지 못했습니다: {error}</p>}
      {!data && !error && <p className="muted">불러오는 중…</p>}
      {data && <GoalView detail={data} />}
    </>
  )
}

function GoalView({ detail }: { detail: GoalDetail }) {
  const { goal, nextAction, pendingAssessmentId, diagnostic, steps } = detail
  const next = NEXT_ACTION[nextAction]
  return (
    <>
      <h1>{goal.subject}</h1>
      <p>{goal.goalText}</p>
      <p className="muted">
        현재 <Level value={goal.currentLevel} /> → 목표 <Level value={goal.targetLevel} />
        {goal.deadline && ` · 마감 ${goal.deadline}`}
      </p>
      <Progress percent={goal.progressPercent} label="전체 진행률" />

      <section className={`banner ${nextAction === 'LEARNING' ? 'ok' : ''}`}>
        <strong>{next.title}</strong>
        <code>{next.hint(goal.goalId, pendingAssessmentId)}</code>
      </section>

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
                  난이도 {'●'.repeat(s.difficulty)}{'○'.repeat(5 - s.difficulty)} · 약 {formatMinutes(s.estimatedMinutes)}
                </div>
                {s.practiceTasks.length > 0 && (
                  <ul className="tasks">{s.practiceTasks.map((t) => <li key={t}>{t}</li>)}</ul>
                )}
              </li>
            ))}
          </ol>
        </section>
      )}
    </>
  )
}
