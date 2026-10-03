#!/usr/bin/env python3
"""예시/모델 출력을 Java Agent(EvaluatorAgent, CurriculumAgent)와 같은 규칙으로 검증한다.

예시 형식 (JSONL 한 줄): {"id":1,"task":"diagnostic|grading|curriculum","input":{...},"output":{...}}
사용: python validate.py examples/*.jsonl   -> clean.jsonl 로 통과분 저장, 실패 사유 집계 출력
"""
import json
import sys

TYPES = {"CONCEPT", "SYNTAX", "UNDERSTANDING", "APPLICATION", "PROBLEM_SOLVING", "CODE_QUALITY", "DEBUGGING"}
CRITERIA = ["conceptUnderstanding", "reasoningQuality", "problemSolving", "codeQuality", "structure", "communication"]


class Invalid(Exception):
    pass


def check(cond, msg):
    if not cond:
        raise Invalid(msg)


def int_in(node, field, lo, hi, label):
    v = node.get(field)
    check(isinstance(v, int) and not isinstance(v, bool) and lo <= v <= hi, f"{label}: {field}는 {lo}~{hi} 정수여야 함")
    return v


def text(node, field):
    v = node.get(field)
    return v.strip() if isinstance(v, str) else ""


def strings(node, field, limit):
    v = node.get(field)
    return [s.strip() for s in v if isinstance(s, str) and s.strip()][:limit] if isinstance(v, list) else []


def diagnostic(inp, out):
    check({"subject", "goal", "targetLevel", "prerequisites"} <= inp.keys(), "diagnostic 입력 필드 부족")
    qs = out.get("questions")
    check(isinstance(qs, list) and 6 <= len(qs) <= 10, "questions는 6~10개 (프롬프트 규칙)")
    last = 0
    for i, q in enumerate(qs, 1):
        label = f"문제 {i}"
        check(text(q, "area") and text(q, "question"), f"{label}: area/question 필수")
        check(q.get("type") in TYPES, f"{label}: type은 {sorted(TYPES)} 중 하나")
        d = int_in(q, "difficulty", 1, 5, label)
        check(d >= last - 1, f"{label}: 쉬운 문제에서 어려운 문제 순이어야 함")
        last = max(last, d)
        check(2 <= len(strings(q, "keyPoints", 6)) <= 4, f"{label}: keyPoints는 2~4개")


def grading(inp, out):
    items = inp.get("items")
    check(isinstance(items, list) and items, "grading 입력 items 필요")
    ids = [it["questionId"] for it in items]
    results = out.get("questionResults")
    check(isinstance(results, list), "questionResults 필요")
    got = []
    for r in results:
        qid = r.get("questionId")
        check(isinstance(qid, int) and qid in ids, f"채점 대상이 아닌 questionId: {qid}")
        int_in(r, "score", 0, 100, f"문제 {qid}")
        check(text(r, "feedback"), f"문제 {qid}: feedback 필수")
        got.append(qid)
    check(sorted(got) == sorted(ids), "모든 questionId를 정확히 한 번씩 채점해야 함")
    c = out.get("criteria")
    check(isinstance(c, dict), "criteria 필요")
    for k in CRITERIA:
        int_in(c, k, 0, 100, "criteria")
    for k in ("strengths", "weaknesses"):
        check(len(strings(out, k, 99)) <= 3, f"{k}는 최대 3개")


def curriculum(inp, out):
    check({"subject", "goal", "targetLevel", "currentLevel", "areaScores"} <= inp.keys(), "curriculum 입력 필드 부족")
    steps = out.get("steps")
    check(isinstance(steps, list) and 6 <= len(steps) <= 15, "steps는 6~15개 (프롬프트 규칙)")
    for i, s in enumerate(steps, 1):
        label = f"Step {i}"
        check(text(s, "title") and text(s, "objective"), f"{label}: title/objective 필수")
        int_in(s, "difficulty", 1, 5, label)
        int_in(s, "estimatedMinutes", 10, 6000, label)
        check(1 <= len(strings(s, "practiceTasks", 5)) <= 3, f"{label}: practiceTasks는 1~3개")
    low = [a["area"] for a in inp["areaScores"] if a["score"] < 60]
    check(not low or any(a in (s["title"] + s["objective"]) for a in low for s in steps), "점수 낮은 영역이 Step에 반영되지 않음")


TASKS = {"diagnostic": diagnostic, "grading": grading, "curriculum": curriculum}


def validate(example):
    """통과하면 None, 아니면 사유 문자열."""
    try:
        check(example.get("task") in TASKS, "task는 diagnostic|grading|curriculum")
        check(isinstance(example.get("input"), dict) and isinstance(example.get("output"), dict), "input/output은 객체")
        TASKS[example["task"]](example["input"], example["output"])
        return None
    except (Invalid, KeyError, TypeError, AttributeError) as e:
        return f"{type(e).__name__}: {e}"


if __name__ == "__main__":
    ok, bad = [], {}
    for path in sys.argv[1:]:
        for line in open(path, encoding="utf-8"):
            if not line.strip():
                continue
            ex = json.loads(line)
            why = validate(ex)
            if why is None:
                ok.append(ex)
            else:
                bad.setdefault(why, []).append(ex.get("id"))
    with open("clean.jsonl", "w", encoding="utf-8") as f:
        for ex in ok:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")
    print(f"통과 {len(ok)}개 -> clean.jsonl, 실패 {sum(map(len, bad.values()))}개")
    for why, ids in sorted(bad.items(), key=lambda kv: -len(kv[1])):
        print(f"  {len(ids):3d}  {why}  (id: {ids[:5]})")
