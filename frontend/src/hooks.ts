import { useCallback, useEffect, useState } from 'react'

export type Route = { page: 'list' } | { page: 'goal'; goalId: number } | { page: 'settings' } | { page: 'patterns' } | { page: 'stats' } | { page: 'coding' } | { page: 'problem'; problemId: number }

/** '#/' 목록, '#/goals/3' 상세, '#/settings' 설정. 화면이 몇 개 안 돼 라우터 라이브러리 없이 해시로 나눈다. */
export function useHashRoute(): Route {
  const parse = (): Route => {
    const goal = location.hash.match(/^#\/goals\/(\d+)$/)
    if (goal) return { page: 'goal', goalId: Number(goal[1]) }
    const problem = location.hash.match(/^#\/coding\/(\d+)$/)
    if (problem) return { page: 'problem', problemId: Number(problem[1]) }
    if (location.hash === '#/coding') return { page: 'coding' }
    if (location.hash === '#/stats') return { page: 'stats' }
    if (location.hash === '#/patterns') return { page: 'patterns' }
    return location.hash === '#/settings' ? { page: 'settings' } : { page: 'list' }
  }
  const [route, setRoute] = useState(parse)
  useEffect(() => {
    const onChange = () => setRoute(parse())
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return route
}

/** 조회 상태(로딩/오류/데이터). key가 바뀌면 비우고 다시 읽고, reload()는 화면을 비우지 않고 다시 읽는다. */
export function useLoad<T>(load: () => Promise<T>, key: unknown) {
  const [state, setState] = useState<{ data?: T; error?: string }>({})
  const [nonce, setNonce] = useState(0)
  useEffect(() => setState({}), [key])
  useEffect(() => {
    let cancelled = false
    load().then(
      (data) => !cancelled && setState({ data }),
      (e: Error) => !cancelled && setState({ error: e.message }),
    )
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, nonce])
  const reload = useCallback(() => setNonce((n) => n + 1), [])
  return { ...state, reload }
}

/** 버튼 동작(POST 등)의 진행/오류 상태. 실패하면 undefined를 돌려주고 error에 사유를 담는다. */
export function useAction() {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const run = async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setPending(true)
    setError(null)
    try {
      return await fn()
    } catch (e) {
      setError((e as Error).message)
      return undefined
    } finally {
      setPending(false)
    }
  }
  return { pending, error, run }
}
