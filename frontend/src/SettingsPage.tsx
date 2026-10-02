import { useEffect, useState } from 'react'
import { fetchLlmSettings, testLlm, updateLlmSettings, type LlmSettings, type LlmTestResult, type Provider } from './api'
import { useAction, useLoad } from './hooks'
import { CLAUDE_MODEL_SUGGESTIONS, PROVIDER_LABEL } from './labels'

export function SettingsPage({ onSaved }: { onSaved: () => void }) {
  const { data, error, reload } = useLoad<LlmSettings>(fetchLlmSettings, 'settings')
  return (
    <>
      <a href="#/" className="back">← 목록</a>
      <h1>설정</h1>
      {error && <p className="error" role="alert">설정을 불러오지 못했습니다: {error}</p>}
      {!data && !error && <p className="muted">불러오는 중…</p>}
      {data && <LlmForm settings={data} onSaved={() => { reload(); onSaved() }} />}
    </>
  )
}

function LlmForm({ settings, onSaved }: { settings: LlmSettings; onSaved: () => void }) {
  const [provider, setProvider] = useState<Provider>(settings.provider)
  const [ollamaModel, setOllamaModel] = useState(settings.ollamaModel ?? '')
  const [claudeModel, setClaudeModel] = useState(settings.claudeModel ?? '')
  const [saved, setSaved] = useState(false)
  const [test, setTest] = useState<LlmTestResult | null>(null)
  const save = useAction()
  const check = useAction()

  // 저장된 설정이 바뀌면(저장 후 다시 읽기) 폼을 서버 값에 맞춘다
  useEffect(() => {
    setProvider(settings.provider)
    setOllamaModel(settings.ollamaModel ?? '')
    setClaudeModel(settings.claudeModel ?? '')
  }, [settings])

  const dirty = provider !== settings.provider
    || ollamaModel.trim() !== (settings.ollamaModel ?? '')
    || claudeModel.trim() !== (settings.claudeModel ?? '')

  async function submit() {
    setSaved(false)
    setTest(null)
    const res = await save.run(() => updateLlmSettings({ provider, ollamaModel, claudeModel }))
    if (res) { setSaved(true); onSaved() }
  }

  async function runTest() {
    setTest(null)
    const res = await check.run(testLlm)
    if (res) setTest(res)
  }

  return (
    <section aria-labelledby="llm">
      <h2 id="llm">LLM 선택</h2>
      <p className="muted small">
        진단 문제 생성, 채점, 커리큘럼 생성이 모두 여기서 고른 모델로 동작합니다. 앱을 다시 시작하지 않아도 바로 적용됩니다.
      </p>

      <fieldset className="providers">
        <legend className="sr-only">사용할 LLM</legend>
        <label className={`provider ${provider === 'OLLAMA' ? 'on' : ''}`}>
          <input type="radio" name="provider" checked={provider === 'OLLAMA'} onChange={() => setProvider('OLLAMA')} />
          <span><strong>{PROVIDER_LABEL.OLLAMA}</strong><br />
            <span className="muted small">내 PC에서 실행. 무료이고 데이터가 밖으로 나가지 않습니다.</span></span>
        </label>
        <label className={`provider ${provider === 'CLAUDE' ? 'on' : ''} ${settings.claudeAvailable ? '' : 'off'}`}>
          <input type="radio" name="provider" disabled={!settings.claudeAvailable}
            checked={provider === 'CLAUDE'} onChange={() => setProvider('CLAUDE')} />
          <span><strong>{PROVIDER_LABEL.CLAUDE}</strong><br />
            <span className="muted small">
              {settings.claudeAvailable
                ? '품질이 높습니다. 호출마다 API 비용이 듭니다.'
                : 'API 키가 없어 선택할 수 없습니다. .env의 ANTHROPIC_API_KEY를 설정하고 앱을 다시 시작하세요.'}
            </span></span>
        </label>
      </fieldset>

      <div className="form">
        <label>Ollama 모델 <span className="muted small">(비워 두면 기본값)</span>
          <input value={ollamaModel} onChange={(e) => setOllamaModel(e.target.value)}
            placeholder={settings.defaults.ollamaModel} maxLength={100} />
        </label>
        <label>Claude 모델 <span className="muted small">(비워 두면 기본값)</span>
          <input list="claude-models" value={claudeModel} onChange={(e) => setClaudeModel(e.target.value)}
            placeholder={settings.defaults.claudeModel} maxLength={100} disabled={!settings.claudeAvailable} />
          <datalist id="claude-models">{CLAUDE_MODEL_SUGGESTIONS.map((m) => <option key={m} value={m} />)}</datalist>
        </label>
        <p className="muted small">API 키는 보안상 화면에서 입력하지 않습니다. 서버의 .env로만 설정합니다.</p>

        {save.error && <p className="error" role="alert">{save.error}</p>}
        {saved && !dirty && <p className="ok-msg" role="status">저장했습니다. 지금부터 {PROVIDER_LABEL[settings.provider]} · {settings.activeModel}을(를) 사용합니다.</p>}
        <div className="actions">
          <button className="primary" onClick={submit} disabled={save.pending || !dirty}>
            {save.pending ? '저장 중…' : '저장'}
          </button>
          <button onClick={runTest} disabled={check.pending || dirty} title={dirty ? '먼저 저장하세요' : undefined}>
            {check.pending ? '테스트 중…' : '연결 테스트'}
          </button>
          {dirty && <span className="muted small">저장한 뒤에 테스트할 수 있습니다</span>}
        </div>

        {check.error && <p className="error" role="alert">{check.error}</p>}
        {test && (test.ok
          ? <p className="ok-msg" role="status">✔ 연결됨 · {PROVIDER_LABEL[test.provider]} · {test.model} · {test.elapsedMs}ms · 응답: “{test.reply}”</p>
          : <p className="error" role="alert">✘ 연결 실패 · {PROVIDER_LABEL[test.provider]} · {test.model}<br />{test.message}</p>)}
      </div>
    </section>
  )
}
