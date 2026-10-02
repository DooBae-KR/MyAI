import { useState, type FormEvent } from 'react'
import {
  createLecture, createProblem, fetchProblem, fetchProblems, submitSolution,
  type CodingProblemDetail, type CodingProblemSummary, type Review, type Submission,
} from './api'
import { useAction, useLoad } from './hooks'

const LANGUAGES = ['Java', 'Python', 'C++', 'JavaScript', 'Kotlin', 'Go']
const MAX_CODE = 20000 // 서버 제한과 같다 (CodingTestService.MAX_CODE_CHARS)
const MAX_EXPLANATION = 4000

const meta = (p: { difficulty: string | null; category: string | null; algorithm: string | null; dataStructure: string | null; language: string | null; estimatedTime: number | null }) =>
  [p.difficulty, p.category, p.algorithm, p.dataStructure, p.language, p.estimatedTime ? `약 ${p.estimatedTime}분` : null].filter(Boolean) as string[]

/** 안전한 링크만 연다. 서버도 http(s)만 저장하지만 화면에서도 한 번 더 확인한다. */
const isHttp = (url: string | null): url is string => !!url && /^https?:\/\//i.test(url)

// ---- 목록 + 등록 ----

export function CodingPage() {
  const { data, error } = useLoad<CodingProblemSummary[]>(fetchProblems, 'problems')
  return (
    <>
      <a href="#/" className="back">← 목록</a>
      <h1>코딩테스트</h1>
      <p className="muted small">
        문제의 <strong>원문은 저장하지 않습니다.</strong> 출처와 제목, 난이도, 태그, 그리고 직접 쓴 요약만 등록하면 AI가 특강을 만들고 내 풀이를 비교해 줍니다.
        원문은 원래 사이트에서 확인하세요.
      </p>
      <details className="new-goal" open={data?.length === 0}>
        <summary>{data?.length === 0 ? '첫 문제를 등록하세요' : '+ 문제 등록'}</summary>
        <NewProblemForm />
      </details>

      {error && <p className="error" role="alert">목록을 불러오지 못했습니다: {error}</p>}
      {!data && !error && <p className="muted">불러오는 중…</p>}
      <ul className="cards">
        {data?.map((p) => (
          <li key={p.id}>
            <a className="card" href={`#/coding/${p.id}`}>
              <div className="card-head">
                <h2>{p.title}</h2>
                <span className="muted small">{p.hasLecture ? '특강 있음' : '특강 없음'} · 제출 {p.submissionCount}</span>
              </div>
              <div className="chips">{meta(p).map((m) => <span key={m} className="chip">{m}</span>)}</div>
            </a>
          </li>
        ))}
      </ul>
    </>
  )
}

function NewProblemForm() {
  const [form, setForm] = useState({ title: '', source: '', sourceUrl: '', difficulty: '', category: '', algorithm: '',
    dataStructure: '', language: 'Java', estimatedTime: '', summary: '' })
  const { pending, error, run } = useAction()
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    const created = await run(() => createProblem({
      ...form, estimatedTime: form.estimatedTime ? Number(form.estimatedTime) : undefined,
    }))
    if (created) location.hash = `#/coding/${created.id}`
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>제목 <input required value={form.title} onChange={set('title')} maxLength={200} placeholder="예: 미로 탐색" /></label>
      <div className="row">
        <label>출처 <span className="muted small">(선택)</span><input value={form.source} onChange={set('source')} maxLength={50} placeholder="프로그래머스, 백준 …" /></label>
        <label>출처 주소 <span className="muted small">(선택)</span><input type="url" value={form.sourceUrl} onChange={set('sourceUrl')} maxLength={500} placeholder="https://" /></label>
      </div>
      <div className="row">
        <label>난이도 <input value={form.difficulty} onChange={set('difficulty')} maxLength={30} placeholder="Lv.2, Gold 3 …" /></label>
        <label>분류 <input value={form.category} onChange={set('category')} maxLength={100} placeholder="그래프, DP …" /></label>
      </div>
      <div className="row">
        <label>알고리즘 <input value={form.algorithm} onChange={set('algorithm')} maxLength={200} placeholder="BFS" /></label>
        <label>자료구조 <input value={form.dataStructure} onChange={set('dataStructure')} maxLength={200} placeholder="Queue" /></label>
      </div>
      <div className="row">
        <label>풀이 언어
          <select value={form.language} onChange={set('language')}>{LANGUAGES.map((l) => <option key={l}>{l}</option>)}</select>
        </label>
        <label>예상 시간(분) <input type="number" min={1} max={1000} value={form.estimatedTime} onChange={set('estimatedTime')} /></label>
      </div>
      <label>내가 쓴 문제 요약 <span className="muted small">(문제 원문을 붙여 넣지 말고 직접 요약해 주세요. 입력 크기, 제한 같은 핵심 조건을 쓰면 특강이 정확해집니다)</span>
        <textarea rows={5} maxLength={2000} value={form.summary} onChange={set('summary')} />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button className="primary" type="submit" disabled={pending || !form.title.trim()}>{pending ? '등록 중…' : '문제 등록'}</button>
    </form>
  )
}

// ---- 문제 상세: 특강 + 풀이 제출 ----

export function ProblemPage({ problemId }: { problemId: number }) {
  const { data, error, reload } = useLoad<CodingProblemDetail>(() => fetchProblem(problemId), problemId)
  return (
    <>
      <a href="#/coding" className="back">← 문제 목록</a>
      {error && <p className="error" role="alert">문제를 불러오지 못했습니다: {error}</p>}
      {!data && !error && <p className="muted">불러오는 중…</p>}
      {data && <ProblemView problem={data} onChanged={reload} />}
    </>
  )
}

function ProblemView({ problem: p, onChanged }: { problem: CodingProblemDetail; onChanged: () => void }) {
  return (
    <>
      <h1>{p.title}</h1>
      <div className="chips">
        {meta(p).map((m) => <span key={m} className="chip">{m}</span>)}
        {p.source && (isHttp(p.sourceUrl)
          ? <a className="chip" href={p.sourceUrl} target="_blank" rel="noopener noreferrer">{p.source} ↗ 원문 보기</a>
          : <span className="chip">{p.source}</span>)}
      </div>
      {p.summary && <p className="summary"><span className="muted small">내가 쓴 요약</span><br />{p.summary}</p>}

      <LectureSection problem={p} onChanged={onChanged} />
      <SubmitSection problem={p} onChanged={onChanged} />
      <History submissions={p.submissions} />
    </>
  )
}

function LectureSection({ problem: p, onChanged }: { problem: CodingProblemDetail; onChanged: () => void }) {
  const make = useAction()
  async function generate() {
    if (await make.run(() => createLecture(p.id))) onChanged()
  }
  return (
    <section aria-labelledby="lecture">
      <h2 id="lecture">AI 특강 {p.lecture && <span className="muted small">17단계</span>}</h2>
      {!p.lecture && <p className="muted small">"왜 이 코드를 쓰는가"를 문제 조건 → 사고 → 알고리즘 → 자료구조 → 코드 순서로 풀어 줍니다.</p>}
      {make.error && <p className="error" role="alert">{make.error}</p>}
      <div className="actions">
        <button className="primary" onClick={generate} disabled={make.pending}>
          {make.pending ? '특강 만드는 중…' : p.lecture ? '다시 만들기' : '특강 만들기'}
        </button>
        {make.pending && <span className="muted small" role="status">17단계라 모델에 따라 1~3분 걸릴 수 있습니다</span>}
      </div>
      {p.lecture && (
        <>
          {p.lecture.assumptions.length > 0 && (
            <div className="banner">
              <strong>이 특강이 가정한 것</strong>
              <ul className="plain">{p.lecture.assumptions.map((a) => <li key={a}>{a}</li>)}</ul>
              <span className="muted small">문제 원문이 아니라 요약을 바탕으로 만들어서, 실제 문제와 다르면 요약을 고쳐 다시 만드세요.</span>
            </div>
          )}
          {p.lecture.sections.map((s) => (
            <details key={s.no} className="lecture-step" open={s.no === 1}>
              <summary><span className="seq">{s.no}</span> {s.title}</summary>
              <RichText text={s.body} />
            </details>
          ))}
        </>
      )}
    </section>
  )
}

/** 본문의 ``` 코드펜스는 코드 블록으로, 나머지는 줄바꿈을 살린 글로 보여 준다. React가 텍스트를 이스케이프한다. */
function RichText({ text }: { text: string }) {
  return (
    <>
      {text.split('```').map((part, i) =>
        i % 2 === 1
          ? <pre key={i}><code>{part.replace(/^[A-Za-z0-9+#.-]*\n/, '')}</code></pre>
          : part.trim() && <p key={i} className="prose">{part.trim()}</p>)}
    </>
  )
}

function SubmitSection({ problem: p, onChanged }: { problem: CodingProblemDetail; onChanged: () => void }) {
  const [language, setLanguage] = useState(p.language && LANGUAGES.includes(p.language) ? p.language : 'Java')
  const [code, setCode] = useState('')
  const [explanation, setExplanation] = useState('')
  const [result, setResult] = useState<Submission | null>(null)
  const submit = useAction()

  async function send() {
    setResult(null)
    const res = await submit.run(() => submitSolution(p.id, { language, code, explanation }))
    if (res) { setResult(res); setCode(''); setExplanation(''); onChanged() }
  }

  return (
    <section aria-labelledby="solve">
      <h2 id="solve">내 풀이 비교</h2>
      <p className="muted small">
        풀이 코드를 제출하면 권장 접근과 8개 항목으로 비교하고 차이의 이유를 설명합니다.
        <strong> 코드를 실행해 채점하는 것이 아니라 읽고 분석한 의견</strong>이니, 정답 여부는 원래 사이트에서 확인하세요.
      </p>
      <div className="form">
        <label>언어
          <select value={language} onChange={(e) => setLanguage(e.target.value)}>{LANGUAGES.map((l) => <option key={l}>{l}</option>)}</select>
        </label>
        <label>풀이 코드
          <textarea className="code" rows={12} spellCheck={false} maxLength={MAX_CODE} value={code} onChange={(e) => setCode(e.target.value)} />
          <span className="muted small right">{code.length}/{MAX_CODE}</span>
        </label>
        <label>접근 설명 <span className="muted small">(선택: 어떻게 생각해서 이렇게 풀었는지)</span>
          <textarea rows={3} maxLength={MAX_EXPLANATION} value={explanation} onChange={(e) => setExplanation(e.target.value)} />
        </label>
        {submit.error && <p className="error" role="alert">{submit.error} (작성한 코드는 그대로 남아 있습니다. 다시 제출할 수 있습니다.)</p>}
        <div className="actions">
          <button className="primary" onClick={send} disabled={submit.pending || !code.trim()}>{submit.pending ? '분석 중…' : '제출하고 비교'}</button>
          {submit.pending && <span className="muted small" role="status">모델에 따라 최대 1~2분 걸립니다</span>}
        </div>
      </div>
      {result?.review && <ReviewView review={result.review} />}
    </section>
  )
}

const STATUS_LABEL = { SAME: '같음', DIFFERENT: '다름', UNKNOWN: '판단 어려움' } as const

function ReviewView({ review: r }: { review: Review }) {
  return (
    <div className="review" role="region" aria-label="풀이 비교 결과">
      <div className="two">
        <div><h3>내 풀이</h3><p>{r.userApproach}</p></div>
        <div><h3>권장 접근</h3><p>{r.recommendedApproach}</p></div>
      </div>
      <table className="compare">
        <thead><tr><th>항목</th><th>내 풀이</th><th>권장</th><th>차이</th><th>이유</th></tr></thead>
        <tbody>
          {r.comparisons.map((c) => (
            <tr key={c.item}>
              <th scope="row">{c.item}</th><td>{c.mine}</td><td>{c.recommended}</td>
              <td><span className={`badge cmp-${c.status.toLowerCase()}`}>{STATUS_LABEL[c.status]}</span></td><td>{c.reason}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {r.mistakes.length > 0 && <><h3>실수·위험 지점</h3><ul>{r.mistakes.map((m) => <li key={m}>{m}</li>)}</ul></>}
      {r.nextSteps.length > 0 && <><h3>다음 연습</h3><ul>{r.nextSteps.map((m) => <li key={m}>{m}</li>)}</ul></>}
      {r.similarProblems.length > 0 && <><h3>비슷한 유형</h3><ul>{r.similarProblems.map((m) => <li key={m}>{m}</li>)}</ul></>}
      <p className="summary"><strong>총평</strong><br />{r.summary}</p>
      <p className="muted small">코드를 실행하지 않고 읽고 분석한 의견입니다.</p>
    </div>
  )
}

function History({ submissions }: { submissions: Submission[] }) {
  if (submissions.length === 0) return null
  return (
    <section aria-labelledby="history">
      <h2 id="history">제출 기록 <span className="muted small">{submissions.length}개</span></h2>
      {submissions.map((s) => (
        <details key={s.id} className="lecture-step">
          <summary>{new Date(s.createdAt).toLocaleString('ko-KR')} · {s.language}</summary>
          <pre><code>{s.code}</code></pre>
          {s.explanation && <p className="prose">{s.explanation}</p>}
          {s.review && <ReviewView review={s.review} />}
        </details>
      ))}
    </section>
  )
}
