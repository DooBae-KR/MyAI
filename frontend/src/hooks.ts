import { useCallback, useEffect, useState } from 'react'

/** '#/' 는 목록, '#/goals/3' 는 상세. 화면이 둘뿐이라 라우터 라이브러리 없이 해시로 나눈다. */
export function useHashRoute(): number | null {
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
