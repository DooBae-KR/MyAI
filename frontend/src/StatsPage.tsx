import { fetchStats, type Stats } from './api'
import { useLoad } from './hooks'
import { PATTERN_STATUS_LABEL, scoreTone } from './labels'

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
        <Tile label="학습 시간" value={formatStudy(t.studyMinutes)} />
        <Tile label="진단 답변" value={t.answers} />
        <Tile label="코딩 문제" value={t.problems} />
        <Tile label="코딩 풀이 제출" value={t.submissions} />
      </ul>

      <section>
        <h2>최근 14일 활동</h2>
        <div className="activity" role="img" aria-label={`최근 14일 활동: ${s.activity.map((d) => `${d.date} ${d.answers + d.submissions}건 ${d.studyMinutes}분`).join(', ')}`}>
          {s.activity.map((d) => {
            const n = d.answers + d.submissions
            return (
              <div key={d.date} className="day" title={`${d.date}: 답변 ${d.answers}, 풀이 ${d.submissions}, 학습 ${d.studyMinutes}분`}>
                <div className="col" style={{ height: `${(n / max) * 100}%` }} />
                <span className="muted small">{d.date.slice(8)}</span>
              </div>
            )
          })}
        </div>
        <p className="muted small">답변 + 코딩 풀이 제출 수. 막대에 올리면 그날 학습 시간(분)도 보입니다 (날짜: 서버 시간 기준)</p>
      </section>

      {s.reviews.length > 0 && (
        <section>
          <h2>복습할 Step <span className="muted small">{s.reviews.length}개</span></h2>
          <p className="muted small">확인 문제에서 합격선에 못 미친 Step입니다. 다시 학습한 뒤 새 문제로 도전하세요. 점수가 낮은 순입니다.</p>
          <ul className="reviews">
            {s.reviews.map((r) => (
              <li key={r.stepId}>
                <a href={`#/goals/${r.goalId}`}>{r.subject} · Step {r.seq} {r.title}</a>
                <span className="muted small">
                  {r.lastScore === null ? '채점 기록 없음' : `마지막 ${r.lastScore}점${r.daysAgo === null ? '' : r.daysAgo === 0 ? ' · 오늘' : ` · ${r.daysAgo}일 전`}`}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h2>진단 정답률 추이</h2>
        {s.diagnosticTrends.length === 0 && <p className="muted">채점된 진단이 아직 없습니다. 진단을 다시 받으면 변화가 선으로 보입니다.</p>}
        {s.diagnosticTrends.map((tr) => <TrendChart key={tr.goalId} trend={tr} />)}
      </section>

      {s.weakAreas.length > 0 && (
        <section>
          <h2>보강이 필요한 영역</h2>
          <p className="muted small">목표별 가장 최근 진단에서 점수가 낮은 영역입니다.</p>
          <ul className="cats">
            {s.weakAreas.map((w) => (
              <li key={`${w.subject}-${w.area}`}>
                <span>{w.subject} · {w.area}</span>
                <div className={`bar ${scoreTone(w.score)}`}><div style={{ width: `${w.score}%` }} /></div>
                <span className="score">{w.score}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

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

function TrendChart({ trend }: { trend: Stats['diagnosticTrends'][number] }) {
  const w = 300, h = 90, pad = 8
  const pts = trend.points
  const x = (i: number) => (pts.length === 1 ? w / 2 : pad + (i * (w - 2 * pad)) / (pts.length - 1))
  const y = (v: number) => h - pad - (v / 100) * (h - 2 * pad)
  const label = pts.map((p) => `${p.date} ${p.correctness}점`).join(', ')
  return (
    <figure className="trend">
      <figcaption><strong>{trend.subject}</strong> <span className="muted small">진단 {pts.length}회, 최근 {pts[pts.length - 1].correctness}점</span></figcaption>
      <svg viewBox={`0 0 ${w} ${h}`} role="img" aria-label={`${trend.subject} 진단 정답률: ${label}`}>
        <line x1={pad} x2={w - pad} y1={y(0)} y2={y(0)} className="axis" />
        <polyline fill="none" className="line" points={pts.map((p, i) => `${x(i)},${y(p.correctness)}`).join(' ')} />
        {pts.map((p, i) => <circle key={p.date + i} cx={x(i)} cy={y(p.correctness)} r="3.5" className="dot"><title>{`${p.date}: ${p.correctness}점`}</title></circle>)}
      </svg>
    </figure>
  )
}

/** 학습 시간(분)을 "1시간 30분"처럼. 학습 자료를 열어 둔 시간만 센다. */
function formatStudy(minutes: number) {
  if (minutes < 60) return `${minutes}분`
  const h = Math.floor(minutes / 60), m = minutes % 60
  return m === 0 ? `${h}시간` : `${h}시간 ${m}분`
}
