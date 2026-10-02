import { useState, type FormEvent } from 'react'
import { createGoal, type Level } from './api'
import { useAction } from './hooks'
import { LEVEL_LABEL } from './labels'

export function NewGoalForm() {
  const [subject, setSubject] = useState('')
  const [goal, setGoal] = useState('')
  const [targetLevel, setTargetLevel] = useState<Level | ''>('')
  const [deadline, setDeadline] = useState('')
  const { pending, error, run } = useAction()

  async function submit(e: FormEvent) {
    e.preventDefault()
    const created = await run(() => createGoal({
      subject, goal,
      targetLevel: targetLevel || undefined,
      deadline: deadline || undefined,
    }))
    if (created) location.hash = `#/goals/${created.goalId}`
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>배우고 싶은 분야
        <input required value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="예: Vue, Python, SQL" maxLength={100} />
      </label>
      <label>목표
        <input required value={goal} onChange={(e) => setGoal(e.target.value)} placeholder="예: 실무 수준까지 배우기" maxLength={1000} />
      </label>
      <div className="row">
        <label>목표 수준 <span className="muted small">(선택)</span>
          <select value={targetLevel} onChange={(e) => setTargetLevel(e.target.value as Level | '')}>
            <option value="">미정</option>
            {Object.entries(LEVEL_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </label>
        <label>마감일 <span className="muted small">(선택)</span>
          <input type="date" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
        </label>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <button className="primary" type="submit" disabled={pending || !subject.trim() || !goal.trim()}>
        {pending ? '등록 중…' : '목표 등록'}
      </button>
    </form>
  )
}
