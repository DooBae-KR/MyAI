#!/usr/bin/env python3
"""학습 예시를 만들기 위한 시드(JSONL)를 생성한다. 시드 한 줄 = Claude가 예시 하나를 쓰는 주문서.

사용: python seeds.py [작업당_개수=120] > seeds.jsonl
"""
import json
import random
import sys

# 분야 -> 선행 분야 (앱이 DB에서 넘기는 prerequisites 모양을 흉내낸다)
SUBJECTS = {
    "JavaScript": [], "TypeScript": ["JavaScript"], "React": ["JavaScript", "HTML/CSS"], "Vue": ["JavaScript", "HTML/CSS"],
    "Node.js": ["JavaScript"], "Python": [], "FastAPI": ["Python", "HTTP"], "Django": ["Python", "HTTP"],
    "Java": [], "Spring Boot": ["Java", "HTTP"], "JPA": ["Java", "SQL"], "Kotlin": ["Java"],
    "SQL": [], "PostgreSQL": ["SQL"], "Redis": [], "Docker": ["Linux 기초"], "Kubernetes": ["Docker", "Linux 기초"],
    "Git": [], "Linux 기초": [], "HTTP": [], "REST API 설계": ["HTTP"], "알고리즘": ["Python"], "자료구조": ["Python"],
    "C": [], "C++": ["C"], "Rust": ["C"], "Go": [], "Swift": [], "Flutter": ["Dart"], "Dart": [],
    "머신러닝 기초": ["Python", "통계 기초"], "PyTorch": ["Python", "머신러닝 기초"], "프롬프트 엔지니어링": [],
    "테스트 코드 작성": [], "디자인 패턴": ["Java"], "클린 코드": [], "네트워크 기초": [], "운영체제 기초": ["C"],
    "GraphQL": ["HTTP", "JavaScript"], "Next.js": ["React"], "AWS 기초": ["Linux 기초", "네트워크 기초"],
}
GOALS = ["취업 준비", "실무 투입", "사이드 프로젝트 완성", "코딩 테스트 대비", "기초 개념 정리", "팀 코드 리뷰 수준 도달",
         "회사 서비스 직접 운영", "자격증/시험 대비", "개념은 아는데 실전이 약해서 보완", "다른 언어 경험을 이 분야로 확장"]
LEVELS = ["BEGINNER", "INTERMEDIATE", "ADVANCED"]
# 채점 시드의 답변 품질 프로필. injection은 보안 규칙(답변 안의 지시문 무시) 학습용이다.
PROFILES = ["strong", "mixed", "weak", "mixed", "blank_parts", "injection"]


def seeds(per_task, rng):
    names = list(SUBJECTS)
    for task in ("diagnostic", "grading", "curriculum"):
        for i in range(per_task):
            s = names[i % len(names)] if i < len(names) * 2 else rng.choice(names)
            seed = {"task": task, "subject": s, "goal": rng.choice(GOALS), "prerequisites": SUBJECTS[s]}
            if task == "diagnostic":
                seed["targetLevel"] = rng.choice(LEVELS + ["미정"])
            elif task == "grading":
                seed["profile"] = PROFILES[i % len(PROFILES)]
                seed["questionCount"] = rng.randint(3, 8)
            else:
                seed["targetLevel"] = rng.choice(LEVELS)
                seed["currentLevel"] = rng.choice(LEVELS)
            yield seed


if __name__ == "__main__":
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 120
    rng = random.Random(42)
    for k, seed in enumerate(seeds(n, rng)):
        print(json.dumps({"id": k, **seed}, ensure_ascii=False))
