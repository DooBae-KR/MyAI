package com.personal.ai.agent.codingtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.Json;
import com.personal.ai.agent.Prompt;
import com.personal.ai.agent.StructuredLlm;
import com.personal.ai.core.model.AiModel;

import java.util.*;

/** 코딩테스트 특강 생성과 풀이 비교. DB를 모르고, 응답은 검증을 통과한 것만 돌려준다. */
public class CodingTestAgent {

    /** 설계서 16장의 특강 17단계. 제목은 코드가 고정한다. */
    public static final List<String> LECTURE_TITLES = List.of(
            "문제 이해", "문제에서 중요한 조건", "입력 크기 분석", "문제 유형 판별", "가능한 알고리즘",
            "알고리즘 선택 이유", "자료구조 선택 이유", "풀이 전략", "코드 구현", "코드 한 줄/블록 단위 분석",
            "시간복잡도", "공간복잡도", "자주 하는 실수", "다른 풀이 방법", "실전에서 문제를 읽는 방법",
            "비슷한 문제", "핵심 복습");

    /** 설계서 19장의 풀이 비교 항목. */
    public static final List<String> COMPARISON_ITEMS = List.of(
            "알고리즘 선택", "자료구조 선택", "시간복잡도", "공간복잡도", "코드 구조", "가독성", "예외 처리", "문제 이해");

    static final Set<String> STATUSES = Set.of("SAME", "DIFFERENT", "UNKNOWN");
    private static final int MIN_BODY = 30;
    private static final int MAX_BODY = 8000;

    private final StructuredLlm llm;
    private final Prompt lecturePrompt = Prompt.load("coding-test-lecture");
    private final Prompt reviewPrompt = Prompt.load("coding-test-review");

    public CodingTestAgent(AiModel model) {
        this.llm = new StructuredLlm(model);
    }

    // ---- 특강 ----

    public LectureContent lecture(ProblemInfo problem) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("problem", problem);
        input.put("lectureSections", LECTURE_TITLES);
        return llm.call(lecturePrompt, llm.toJson(input), 0.3, this::parseLecture);
    }

    LectureContent parseLecture(JsonNode root) {
        JsonNode items = root.path("sections");
        if (!items.isArray() || items.size() != LECTURE_TITLES.size()) {
            throw new AgentResponseException("sections는 정확히 " + LECTURE_TITLES.size() + "개여야 합니다.");
        }
        List<LectureContent.Section> sections = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String body = Json.text(items.get(i), "body").trim();
            String label = (i + 1) + "단계(" + LECTURE_TITLES.get(i) + ")";
            if (body.length() < MIN_BODY) {
                throw new AgentResponseException(label + ": body가 너무 짧습니다(" + MIN_BODY + "자 이상).");
            }
            if (body.length() > MAX_BODY) {
                throw new AgentResponseException(label + ": body가 너무 깁니다(" + MAX_BODY + "자 이하).");
            }
            sections.add(new LectureContent.Section(i + 1, LECTURE_TITLES.get(i), body)); // 번호와 제목은 우리가 정한다
        }
        return new LectureContent(lecturePrompt.version(), Json.strings(root, "assumptions", 8), sections);
    }

    // ---- 풀이 비교 ----

    public ReviewContent review(ProblemInfo problem, String language, String code, String explanation) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("problem", problem);
        input.put("language", language);
        input.put("code", code);
        input.put("explanation", explanation == null ? "" : explanation);
        input.put("comparisonItems", COMPARISON_ITEMS);
        return llm.call(reviewPrompt, llm.toJson(input), 0.2, this::parseReview);
    }

    ReviewContent parseReview(JsonNode root) {
        String userApproach = required(root, "userApproach", 1000, "내 풀이 요약");
        String recommended = required(root, "recommendedApproach", 1000, "권장 접근");
        String summary = required(root, "summary", 1000, "총평");

        Map<String, JsonNode> byItem = new HashMap<>();
        for (JsonNode c : root.path("comparisons")) {
            byItem.putIfAbsent(Json.text(c, "item").trim(), c);
        }
        List<ReviewContent.Comparison> comparisons = new ArrayList<>();
        for (String item : COMPARISON_ITEMS) {
            JsonNode c = byItem.get(item);
            if (c == null) {
                throw new AgentResponseException("comparisons에 '" + item + "' 항목이 필요합니다. 항목은 " + COMPARISON_ITEMS + " 8개를 모두 이 이름 그대로 써야 합니다.");
            }
            String status = Json.text(c, "status").trim().toUpperCase();
            if (!STATUSES.contains(status)) {
                throw new AgentResponseException("'" + item + "': status는 " + STATUSES + " 중 하나여야 합니다.");
            }
            comparisons.add(new ReviewContent.Comparison(item, required(c, "mine", 600, item + "의 mine"),
                    required(c, "recommended", 600, item + "의 recommended"), status, required(c, "reason", 800, item + "의 reason")));
        }
        return new ReviewContent(reviewPrompt.version(), userApproach, recommended, comparisons,
                Json.strings(root, "mistakes", 6), Json.strings(root, "nextSteps", 5),
                Json.strings(root, "similarProblems", 5), summary);
    }

    private static String required(JsonNode node, String field, int max, String label) {
        String value = Json.text(node, field).trim();
        if (value.isBlank()) {
            throw new AgentResponseException(label + "(" + field + ")이 비어 있습니다.");
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
