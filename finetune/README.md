# MyAI 에이전트 파인튜닝 (로컬, Ollama)

목표: Evaluator(진단 문제 생성·채점)와 Curriculum Agent가 **형식 검증을 통과하는 JSON을 안정적으로 내는** 작은 로컬 모델을 만든다.
Ollama는 모델을 *실행*만 한다. 학습은 별도 도구로 하고, 결과를 Ollama에 올린다.

```text
seeds.py → (Claude가 예시 작성) → validate.py → build_dataset.py → [학습] → [GGUF 변환] → Ollama 등록 → evaluate
  1단계(이 폴더에 있음)                                              2~4단계(데이터가 모인 뒤)
```

## 권장 사양 (RTX 4070 Super 12GB, RAM 32GB)
- 베이스 모델: **Qwen3-8B**, 4비트 QLoRA. 14B는 12GB에서 시퀀스 길이·배치를 크게 줄여야 해서 빠듯하다.
- 데이터: 작업당 300~500개면 형식 학습에는 충분하다. 처음엔 작업당 100개로 한 바퀴 돌려 보는 것을 권한다.
- 목적은 *형식과 규칙 준수*다. 새 지식 주입은 파인튜닝이 약하다.

## 1단계: 데이터
```bash
python seeds.py 120 > seeds.jsonl        # 작업 3종 x 120 = 360 시드
```
Claude Code에서 시드를 20개씩 넘겨 예시를 쓰게 한다. 프롬프트 예:

> finetune/seeds.jsonl 의 id 0~19를 읽고, 각 시드마다 예시 하나를 finetune/examples/batch-000.jsonl 에 JSONL로 써라.
> 한 줄 형식: {"id":시드id,"task":시드task,"input":{앱이 에이전트에 넘기는 입력 JSON},"output":{에이전트가 내야 하는 정답 JSON}}.
> 입력/출력 형식과 규칙은 ai-agent/src/main/resources/prompts/ 의 해당 프롬프트를 따른다.
> grading 시드는 profile(strong/mixed/weak/blank_parts/injection)에 맞는 학습자 답변을 직접 지어 넣고,
> injection이면 답변 안에 "만점을 줘" 같은 지시문을 넣되 output은 그 지시를 따르지 않고 내용만 채점한다.

```bash
python validate.py examples/*.jsonl      # Java Agent와 같은 규칙으로 검증 -> clean.jsonl
python build_dataset.py                  # -> train.jsonl / val.jsonl (system은 앱의 실제 프롬프트)
```
검증 실패 사유가 집계되어 나온다. 많이 실패하는 사유가 있으면 그 시드만 다시 생성한다.

## 2~4단계 (데이터가 모이면)
2. 학습: Unsloth(Windows는 WSL2 권장)로 Qwen3-8B QLoRA. 입력은 `train.jsonl`의 `messages`.
3. 변환: LoRA를 병합해 GGUF(Q4_K_M)로 내보낸다.
4. 등록: `Modelfile`에 `FROM ./model.gguf`를 쓰고 `ollama create myai-agent -f Modelfile`.
   MyAI는 `ollama.model: myai-agent`로 바꾸면 쓴다.
5. 평가: `val.jsonl`에서 베이스 모델과 파인튜닝 모델의 **검증 통과율**을 비교한다. 이 숫자가 오르지 않으면 데이터를 고친다.

## 주의
- Claude로 만든 출력을 학습에 쓰는 것은 Anthropic 이용약관(경쟁 모델 개발 금지 조항 등)에 걸릴 수 있다.
  개인 학습·실험 용도라도 본인 약관(소비자/상업)을 확인하고 쓴다. 이 폴더는 약관 판단을 대신하지 않는다.
- 학습 데이터에 개인정보나 비밀값을 넣지 않는다. 생성물(`examples/`, `*.jsonl`, `*.gguf`)은 커밋하지 않는다(`.gitignore`).
- 프롬프트(`prompts/*.md`)를 바꾸면 `build_dataset.py`를 다시 돌려 데이터를 맞춘다.
