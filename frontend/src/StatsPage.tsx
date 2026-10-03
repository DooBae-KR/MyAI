import { fetchStats, type Stats } from './api'
import { useLoad } from './hooks'
import { PATTERN_STATUS_LABEL } from './labels'

export function StatsPage() {
  const { data, error } = useLoad<Stats>(fetchStats, 'stats')

  return (
    <>
      <a href="#/" className="back">← 목록</a>
      <h1>학습 통계</h1>
      {error && <p className="error" role="alert">통계를 불러오지 못했습니다: {error}</p>}
      {!data && !error && <p className="muted">불러오는 중…</p>}
      {data && <StatsBody stats={data} />}
    </>
  )
}

function StatsBody({ stats: s }: { stats: Stats }) {
  const t = s.totals
  const progress = t.stepsTotal === 0 ? 0 : Math.round((t.stepsCompleted / t.stepsTotal) * 100)
  const max = Math.max(1, ...s.activity.map((d) => d.answers + d.submissions))
  const maxCat = Math.max(1, ...s.codingByCategory.map((c) => c.count))

  return (
    <>
      <ul className="tiles">
        <Tile label="학습 목표" value={t.goals} />
        <Tile label="완료한 단계" value={`${t.stepsCompleted}/${t.stepsTotal}`} sub={`${progress}%`} />
        <Tile label="진단 답변" value={t.answers} />
        <Tile label="코딩 문제" value={t.problems} />
        <Tile label="코딩 풀이 제출" value={t.submissions} />
      </ul>

      <section>
        <h2>최근 14일 활동</h2>
        <div className="activity" role="img" aria-label={`최근 14일 활동: ${s.activity.map((d) => `${d.date} ${d.answers + d.submissions}건`).join(', ')}`}>
          {s.activity.map((d) => {
            const n = d.answers + d.submissions
            return (
              <div key={d.date} className="day" title={`${d.date}: 답변 ${d.answers}, 풀이 ${d.submissions}`}>
                <div className="col" style={{ height: `${(n / max) * 100}%` }} />
                <span className="muted small">{d.date.slice(8)}</span>
              </div>
            )
          })}
        </div>
        <p className="muted small">답변 + 코딩 풀이 제출 수 (날짜: 서버 시간 기준)</p>
      </section>

      <section>
        <h2>풀이한 문제 분류</h2>
        {s.codingByCategory.length === 0 && <p className="muted">아직 제출한 풀이가 없습니다.</p>}
        <ul className="cats">
          {s.codingByCategory.map((c) => (
            <li key={c.name}>
              <span>{c.name}</span>
              <div className="bar mid"><div style={{ width: `${(c.count / maxCat) * 100}%` }} /></div>
              <span className="score">{c.count}</span>
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h2>사고 패턴</h2>
        {s.patternsByStatus.length === 0
          ? <p className="muted">아직 관찰된 패턴이 없습니다. <a href="#/patterns">사고 패턴</a>에서 분석해 보세요.</p>
          : <p>{s.patternsByStatus.map((p) => `${PATTERN_STATUS_LABEL[p.name]} ${p.count}개`).join(' · ')}</p>}
      </section>
    </>
  )
}

function Tile({ label, value, sub }: { label: string; value: number | string; sub?: string }) {
  return (
    <li className="tile">
      <div className="muted small">{label}</div>
      <strong>{value}</strong>
      {sub && <span className="muted small"> {sub}</span>}
    </li>
  )
}
