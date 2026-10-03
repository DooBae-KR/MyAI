import { useEffect, useState } from 'react'
import { fetchLesson, type Lesson } from './api'
import { useAction } from './hooks'
import { useStudyTimer } from './useStudyTimer'

/** Step 학습 자료: 개념 → 예제 → 직접 구현 → 완료 체크. 처음 열 때 AI가 만들고(최대 1~2분), 이후엔 저장본을 보여 준다. */
export function LessonPanel({ stepId }: { stepId: number }) {
  const [lesson, setLesson] = useState<Lesson | null>(null)
  const load = useAction()
  useStudyTimer(stepId, lesson !== null) // 학습 자료를 펼쳐 둔 동안(탭이 보일 때만)의 시간을 기록한다

  async function open(refresh: boolean) {
    const res = await load.run(() => fetchLesson(stepId, refresh))
    if (res) setLesson(res)
  }

  if (!lesson) {
    return (
      <div className="actions">
        <button disabled={load.pending} onClick={() => open(false)}>{load.pending ? '불러오는 중…' : '학습 자료 보기'}</button>
        {load.pending && <span className="muted small" role="status">처음 열면 AI가 만드는 데 모델에 따라 최대 1~2분 걸립니다</span>}
        {load.error && <span className="error" role="alert">{load.error}</span>}
      </div>
    )
  }

  return (
    <div className="lesson">
      <p>{lesson.overview}</p>

      <h4>1. 개념</h4>
      {lesson.concepts.map((c) => <div key={c.title}><strong>{c.title}</strong><p>{c.explanation}</p></div>)}

      <h4>2. 예제</h4>
      {lesson.examples.map((e) => (
        <div key={e.title}>
          <strong>{e.title}</strong>
          <pre><code>{e.code}</code></pre>
          <p>{e.explanation}</p>
        </div>
      ))}

      <h4>3. 직접 구현해 보기</h4>
      <p>{lesson.implementationTask}</p>

      {lesson.commonMistakes.length > 0 && (
        <>
          <h4>자주 하는 실수</h4>
          <ul>{lesson.commonMistakes.map((m) => <li key={m}>{m}</li>)}</ul>
        </>
      )}

      <h4>4. 완료 체크</h4>
      <Checklist stepId={stepId} items={lesson.checklist} />

      <div className="actions">
        <button onClick={() => setLesson(null)}>접기</button>
        <button disabled={load.pending} onClick={() => open(true)}>{load.pending ? '다시 만드는 중…' : '자료 다시 만들기'}</button>
        {load.error && <span className="error" role="alert">{load.error}</span>}
      </div>
    </div>
  )
}

/** 체크 상태는 이 기기의 localStorage에만 임시 저장한다(통과 여부와는 무관, 스스로 점검하는 용도). */
function Checklist({ stepId, items }: { stepId: number; items: string[] }) {
  const key = `lesson-check:${stepId}`
  const [done, setDone] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem(key) ?? '[]') } catch { return [] }
  })
  useEffect(() => {
    try { localStorage.setItem(key, JSON.stringify(done)) } catch { /* 저장소를 못 쓰는 환경 */ }
  }, [key, done])

  return (
    <>
      <ul className="checklist">
        {items.map((it) => (
          <li key={it}>
            <label>
              <input type="checkbox" checked={done.includes(it)}
                onChange={(e) => setDone(e.target.checked ? [...done, it] : done.filter((d) => d !== it))} />
              {' '}{it}
            </label>
          </li>
        ))}
      </ul>
      <p className="muted small">{done.filter((d) => items.includes(d)).length}/{items.length} 확인함 · 모두 확인되면 아래에서 확인 문제를 푸세요.</p>
    </>
  )
}
