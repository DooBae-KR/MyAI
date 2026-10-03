import { useEffect } from 'react'
import { sendStudyTime } from './api'

const FLUSH_EVERY_SECONDS = 30

/** active인 동안 탭이 보이는 시간만 초 단위로 세어 30초마다(그리고 멈출 때) 서버에 더한다. 서버는 한 번에 120초까지만 받는다. */
export function useStudyTimer(stepId: number, active: boolean) {
  useEffect(() => {
    if (!active) return
    let pending = 0
    const flush = () => {
      if (pending > 0) {
        void sendStudyTime(stepId, Math.min(pending, 120))
        pending = 0
      }
    }
    const tick = window.setInterval(() => {
      if (document.visibilityState === 'visible') pending += 1
      if (pending >= FLUSH_EVERY_SECONDS) flush()
    }, 1000)
    const onHide = () => { if (document.visibilityState === 'hidden') flush() }
    document.addEventListener('visibilitychange', onHide)
    return () => {
      window.clearInterval(tick)
      document.removeEventListener('visibilitychange', onHide)
      flush()
    }
  }, [stepId, active])
}
