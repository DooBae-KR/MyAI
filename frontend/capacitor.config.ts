import type { CapacitorConfig } from '@capacitor/cli'

// 앱은 서버에 배포된 대시보드를 그대로 연다(항상 최신, API 주소 설정 불필요).
// 빌드할 때: CAP_SERVER_URL=https://내도메인 npx cap sync android
const url = process.env.CAP_SERVER_URL
if (!url?.startsWith('https://')) throw new Error('CAP_SERVER_URL=https://... 를 설정하세요 (HTTPS만 허용)')

const config: CapacitorConfig = {
  appId: 'kr.doobae.personalai',
  appName: 'Personal AI',
  webDir: 'dist',
  server: { url, cleartext: false },
}
export default config
