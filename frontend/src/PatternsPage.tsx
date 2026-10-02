import { useState } from 'react'
import { analyzePatterns, fetchPatterns, updatePattern, type PatternAnalysis, type PatternView } from './api'
import { useAction, useLoad } from './hooks'
import { PATTERN_STATUS_LABEL, scoreTone } from './labels'

export function PatternsPage() {
  const { data, error, reload } = useLoad<PatternView[]>(fetchPatterns, 'patterns')
  const analyze = useAction()
  const [result, setResult] = useState<PatternAnalysis | null>(null)

  async function runAnalysis() {
    setResult(null)
    const res = await analyze.run(analyzePatterns)
    if (res) { setResult(res); reload() }
  }

  const active = (data ?? []).filter((p) => p.status !== 'DISMISSED')
  const dismissed = (data ?? []).filter((p) => p.status === 'DISMISSED')

  return (
    <>
      <a href="#/" className="back">← 목록</a>
      <h1>사고 패턴</h1>
      <p className="muted small">
        진단 답변과 코딩 풀이에서 반복해서 관찰된 <strong>글쓰기·풀이 습관</strong>을 가설로 보여 줍니다. 성격이나 능력을 평가하는 것이 아니며,
        신뢰도는 관찰된 근거의 통계적 표현입니다. 근거가 서로 다른 문제 3개 이상 쌓이면 "반복 관찰됨"이 되고, 그 전에는 가설입니다.
        맞지 않는 패턴은 언제든 기각할 수 있습니다.
      </p>

      <div className="actions">
        <button className="primary" onClick={runAnalysis} disabled={analyze.pending}>
          {analyze.pending ? '분석 중…' : '새 기록 분석하기'}
        </button>
        {analyze.pending && <span className="muted small" role="status">모델에 따라 최대 1~2분 걸립니다</span>}
      </div>
      {analyze.error && <p className="error" role="alert">{analyze.error} (답변은 분석 전 상태로 남아 있어 다시 시도할 수 있습니다.)</p>}
      {result && (
        <p className="ok-msg" role="status">
          {result.message ?? `답변 ${result.analyzedAnswers}개, 코딩 풀이 ${result.analyzedSubmissions}개를 분석했습니다. 새 패턴 ${result.newPatterns}개, 근거를 더한 기존 패턴 ${result.updatedPatterns}개.`}
        </p>
      )}

      {error && <p className="error" role="alert">패턴을 불러오지 못했습니다: {error}</p>}
      {!data && !error && <p className="muted">불러오는 중…</p>}
      {data && active.length === 0 && (
        <section className="empty">
          <h2>아직 관찰된 패턴이 없습니다</h2>
          <p className="muted">진단 문제에 답하고 채점을 마친 뒤 "새 기록 분석하기"를 눌러 보세요. 답변이 많을수록 정확해집니다.</p>
        </section>
      )}

      <ul className="patterns">
        {active.map((p) => <PatternCard key={p.id} pattern={p} onChanged={reload} />)}
      </ul>

      {dismissed.length > 0 && (
        <details className="dismissed">
          <summary>기각한 패턴 {dismissed.length}개</summary>
          <ul className="patterns">
            {dismissed.map((p) => <PatternCard key={p.id} pattern={p} onChanged={reload} />)}
          </ul>
        </details>
      )}
    </>
  )
}

function PatternCard({ pattern: p, onChanged }: { pattern: PatternView; onChanged: () => void }) {
  const change = useAction()
  const dismissed = p.status === 'DISMISSED'
  const percent = Math.round(p.confidence * 100)

  async function toggle() {
    const res = await change.run(() => updatePattern(p.id, dismissed ? 'HYPOTHESIS' : 'DISMISSED'))
    if (res) onChanged()
  }

  return (
    <li className={`pattern ${dismissed ? 'dismissed-card' : ''}`}>
      <div className="card-head">
        <h2>{p.name}</h2>
        <span className={`badge pat-${p.status.toLowerCase()}`}>{PATTERN_STATUS_LABEL[p.status]}</span>
      </div>
      {p.description && <p>{p.description}</p>}

      <div className="conf">
        <div className={`bar ${scoreTone(percent)}`} role="img" aria-label={`신뢰도 ${p.confidence.toFixed(2)}`}>
          <div style={{ width: `${percent}%` }} />
        </div>
        <span className="muted small">신뢰도 {p.confidence.toFixed(2)} · 근거 {p.evidenceCount}개</span>
      </div>

      {p.improvementStrategy && (
        <div className="strategy"><strong>훈련 제안</strong><br />{p.improvementStrategy}</div>
      )}

      {p.evidence.length > 0 && (
        <details>
          <summary>근거 보기 ({p.evidence.length})</summary>
          <ul className="evidence">
            {p.evidence.map((e) => (
              <li key={`${e.source}-${e.itemId}-${e.quote}`}>
                <div className="muted small">{e.label}</div>
                <blockquote>“{e.quote}”</blockquote>
                {e.note && <div className="muted small">{e.note}</div>}
              </li>
            ))}
          </ul>
        </details>
      )}

      {change.error && <p className="error" role="alert">{change.error}</p>}
      <button onClick={toggle} disabled={change.pending}>
        {change.pending ? '처리 중…' : dismissed ? '되살리기' : '맞지 않아요 (기각)'}
      </button>
    </li>
  )
}
