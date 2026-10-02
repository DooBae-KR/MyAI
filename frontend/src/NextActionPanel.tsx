import { useEffect, useState } from 'react'
import { createCurriculum, createDiagnostic, submitAnswers, type GoalDetail, type GradingResult, type Quiz } from './api'
import { useAction } from './hooks'
import { LEVEL_LABEL, NEXT_TITLE, TYPE_LABEL, difficultyDots, scoreTone } from './labels'

const MAX_ANSWER = 4000 // 서버 제한과 같다 (GradingService.MAX_ANSWER_CHARS)

/** 다음 할 일(nextAction)에 맞는 조작을 보여 준다. 끝나면 onChanged로 상세를 다시 읽는다. */
export function NextActionPanel({ detail, onChanged }: { detail: GoalDetail; onChanged: () => void }) {
  const { goal, nextAction, pendingAssessmentId, pendingQuiz } = detail

  if (nextAction === 'DIAGNOSTIC_NEEDED') {
    return (
      <ActionCard title={NEXT_TITLE[nextAction]} button="진단 문제 생성" busy="진단 문제를 만드는 중… 모델에 따라 최대 1~2분 걸립니다"
        description="목표에 맞는 6~10개의 진단 문제를 만듭니다. 문제에 답하면 현재 수준을 판정하고 커리큘럼을 맞춥니다."
        onRun={() => createDiagnostic(goal.goalId)} onDone={onChanged} />
    )
  }
  if (nextAction === 'ANSWERS_NEEDED' && pendingAssessmentId && pendingQuiz) {
    return <AnswerForm assessmentId={pendingAssessmentId} quiz={pendingQuiz} onContinue={onChanged} />
  }
  if (nextAction === 'CURRICULUM_NEEDED') {
    return (
      <ActionCard title={NEXT_TITLE[nextAction]} button="커리큘럼 생성" busy="커리큘럼을 만드는 중… 모델에 따라 최대 1~2분 걸립니다"
        description="진단 결과로 약한 영역은 앞쪽에 집중하고, 강한 영역은 압축한 개인 커리큘럼을 만듭니다."
        onRun={() => createCurriculum(goal.goalId)} onDone={onChanged} />
    )
  }
  return (
    <section className="banner ok">
      <strong>{NEXT_TITLE.LEARNING}</strong>
      <span>시작 가능한 Step부터 학습하세요.</span>
    </section>
  )
}

function ActionCard(props: { title: string; description: string; button: string; busy: string;
  onRun: () => Promise<unknown>; onDone: () => void }) {
  const { pending, error, run } = useAction()
  return (
    <section className="banner">
      <strong>{props.title}</strong>
      <span>{props.description}</span>
      {error && <p className="error" role="alert">{error}</p>}
      <div>
        <button className="primary" disabled={pending} onClick={async () => (await run(props.onRun)) && props.onDone()}>
          {pending ? '진행 중…' : error ? '다시 시도' : props.button}
        </button>
      </div>
      {pending && <span className="muted small" role="status">{props.busy}</span>}
    </section>
  )
}

/** 답변 초안은 localStorage에 임시 저장해 새로고침이나 오류 후에도 잃지 않는다. 실패해도 동작은 그대로다. */
function useDraft(key: string): [Record<number, string>, (v: Record<number, string>) => void, () => void] {
  const [value, setValue] = useState<Record<number, string>>(() => {
    try { return JSON.parse(localStorage.getItem(key) ?? '{}') } catch { return {} }
  })
  useEffect(() => {
    try { localStorage.setItem(key, JSON.stringify(value)) } catch { /* 저장소를 못 쓰는 환경 */ }
  }, [key, value])
  const clear = () => { try { localStorage.removeItem(key) } catch { /* 무시 */ } }
  return [value, setValue, clear]
}

function AnswerForm({ assessmentId, quiz, onContinue }: { assessmentId: number; quiz: Quiz; onContinue: () => void }) {
  const [answers, setAnswers, clearDraft] = useDraft(`draft:assessment:${assessmentId}`)
  const [graded, setGraded] = useState<GradingResult | null>(null)
  const { pending, error, run } = useAction()
  const filled = quiz.questions.filter((q) => (answers[q.id] ?? '').trim()).length

  async function submit() {
    const items = quiz.questions
      .filter((q) => (answers[q.id] ?? '').trim())
      .map((q) => ({ questionId: q.id, answer: answers[q.id].trim() }))
    const res = await run(() => submitAnswers(assessmentId, items))
    if (res) {
      clearDraft()
      setGraded(res.result)
    }
  }

  if (graded) return <GradedView quiz={quiz} result={graded} onContinue={onContinue} />

  return (
    <section aria-labelledby="quiz">
      <h2 id="quiz">진단 문제 <span className="muted small">{quiz.questions.length}문항</span></h2>
      <p className="muted small">아는 만큼만 적어도 됩니다. 비워 둔 문제는 0점(미응답)으로 처리됩니다. 한 번 제출하면 다시 제출할 수 없습니다.</p>
      <ol className="quiz">
        {quiz.questions.map((q) => (
          <li key={q.id} className="question">
            <div className="muted small">{q.area} · {TYPE_LABEL[q.type] ?? q.type} · 난이도 {difficultyDots(q.difficulty)}</div>
            <p className="qtext">{q.question}</p>
            <label className="sr-only" htmlFor={`a${q.id}`}>{q.id}번 답변</label>
            <textarea id={`a${q.id}`} rows={4} maxLength={MAX_ANSWER} value={answers[q.id] ?? ''}
              onChange={(e) => setAnswers({ ...answers, [q.id]: e.target.value })} placeholder="답변을 입력하세요" />
            <div className="muted small right">{(answers[q.id] ?? '').length}/{MAX_ANSWER}</div>
          </li>
        ))}
      </ol>
      {error && <p className="error" role="alert">{error} (작성한 답변은 그대로 남아 있습니다. 다시 제출할 수 있습니다.)</p>}
      <div className="actions">
        <button className="primary" disabled={pending || filled === 0} onClick={submit}>
          {pending ? '채점 중…' : '제출하고 채점'}
        </button>
        <span className="muted small">{filled}/{quiz.questions.length} 문항 작성</span>
      </div>
      {pending && <p className="muted small" role="status">답변을 채점하는 중… 모델에 따라 최대 1~2분 걸립니다</p>}
    </section>
  )
}

function GradedView({ quiz, result, onContinue }: { quiz: Quiz; result: GradingResult; onContinue: () => void }) {
  const byId = new Map(result.questionResults.map((r) => [r.questionId, r]))
  return (
    <section aria-labelledby="graded">
      <h2 id="graded">채점 결과 <span className="muted small">정답률 {result.correctness}% · {LEVEL_LABEL[result.level]}</span></h2>
      <ul className="results">
        {quiz.questions.map((q) => {
          const r = byId.get(q.id)
          return (
            <li key={q.id}>
              <div className="card-head">
                <strong>{q.id}. {q.area}</strong>
                <span className={`score-chip ${scoreTone(r?.score ?? 0)}`}>{r?.score ?? 0}점</span>
              </div>
              <div className="muted small">{r?.feedback}</div>
            </li>
          )
        })}
      </ul>
      <button className="primary" onClick={onContinue}>다음 단계로</button>
    </section>
  )
}
