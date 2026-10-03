#!/usr/bin/env python3
"""clean.jsonl -> 학습용 train.jsonl / val.jsonl (chat messages 형식).

system은 앱이 실제로 쓰는 프롬프트(ai-agent/src/main/resources/prompts/*.md)를 그대로 읽어 쓴다.
프롬프트를 고치면 이 스크립트를 다시 돌려 데이터를 재생성한다.
사용: python build_dataset.py [clean.jsonl]
"""
import json
import random
import sys
from pathlib import Path

PROMPTS = Path(__file__).resolve().parent.parent / "ai-agent/src/main/resources/prompts"
PROMPT_FILE = {"diagnostic": "evaluator-diagnostic", "grading": "evaluator-grading", "curriculum": "curriculum-agent"}


def load_prompt(name):
    raw = (PROMPTS / f"{name}.md").read_text(encoding="utf-8").replace("\r\n", "\n")
    if raw.startswith("---\n") and "\n---\n" in raw[3:]:
        raw = raw[raw.index("\n---\n", 3) + 5:]
    return raw.strip()


def compact(obj):
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))  # Jackson의 compact JSON과 같은 모양


def main(src):
    system = {t: load_prompt(n) for t, n in PROMPT_FILE.items()}
    examples = [json.loads(l) for l in open(src, encoding="utf-8") if l.strip()]
    rng = random.Random(7)
    rng.shuffle(examples)
    rows = [{"task": e["task"], "messages": [
        {"role": "system", "content": system[e["task"]]},
        {"role": "user", "content": compact(e["input"])},
        {"role": "assistant", "content": compact(e["output"])}]} for e in examples]
    val = [r for i, r in enumerate(rows) if i % 20 == 0]          # 5%는 검증용(학습에 쓰지 않는다)
    train = [r for i, r in enumerate(rows) if i % 20 != 0]
    for name, part in (("train.jsonl", train), ("val.jsonl", val)):
        with open(name, "w", encoding="utf-8") as f:
            for r in part:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"train {len(train)}개, val {len(val)}개")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "clean.jsonl")
