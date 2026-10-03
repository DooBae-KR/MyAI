import { fetchToday, type Today } from './api'
import { useLoad } from './hooks'

/** 설계서 20장의 "오늘의 학습"(할 일 체크리스트)과 "오늘의 특강". 이미 있는 데이터로 서버가 계산한다. */
export function TodayPanel() {
  const { data, error } = useLoad<Today>(fetchToday, 'today')
  if (error) return <p className="error" role="alert">오늘의 학습을 불러오지 못했습니다: {error}</p>
  if (!data) return null

  const open = data.todos.filter((t) => !t.done).length
  return (
    <section className="today" aria-labelledby="today-h">
      <h2 id="today-h">오늘의 학습 <span className="muted small">{open > 0 ? `${open}개 남음` : '오늘 할 일을 모두 마쳤습니다 🎉'}</span></h2>
      {data.todos.length === 0
        ? <p className="muted">지금 해야 할 일이 없습니다. 코딩테스트 문제를 풀어 보세요.</p>
        : (
          <ul className="todos">
            {data.todos.map((t) => (
              <li key={t.label} className={t.done ? 'done' : ''}>
                <span aria-hidden="true">{t.done ? '☑' : '□'}</span>{' '}
                <a href={t.link}>{t.label}</a>
                <span className="sr-only">{t.done ? ' (완료)' : ''}</span>
              </li>
            ))}
          </ul>
        )}
      {data.lecture && (
        <div className="lecture-card">
          <div className="muted small">💻 오늘의 코딩테스트 특강</div>
          <strong>“{data.lecture.title}”</strong>
          <div className="muted small">{data.lecture.reason}</div>
          <a className="button-link" href={`#/coding/${data.lecture.problemId}`}>특강 보기</a>
        </div>
      )}
    </section>
  )
}
