package com.personal.ai.agent;

import com.personal.ai.agent.codingtest.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodingTestAgentTest {

    private static final ProblemInfo PROBLEM = new ProblemInfo("미로 탐색", "프로그래머스", "https://example.com/p/1", null,
            "Lv.2", "그래프", "BFS", "Queue", "Java", "격자에서 시작점에서 도착점까지 최소 이동 횟수를 구한다.", 40);

    private static String body(int i) {
        return "이 단계는 문제의 " + i + "번째 설명이며 충분히 긴 본문을 가지고 있습니다. ```java\nint x = 1;\n```";
    }

    private static String lecture(int count) {
        List<String> sections = new ArrayList<>();
        for (int i = 1; i <= count; i++) sections.add("{\"no\":" + i + ",\"body\":\"" + body(i).replace("\n", "\\n") + "\"}");
        return "{\"assumptions\":[\"N은 최대 10만이라고 가정\",\"\"],\"sections\":[" + String.join(",", sections) + "]}";
    }

    private static String comparison(String item, String status) {
        return "{\"item\":\"" + item + "\",\"mine\":\"DFS\",\"recommended\":\"BFS\",\"status\":\"" + status + "\",\"reason\":\"최단거리가 목표이므로\"}";
    }

    private static String review(List<String> items, String status) {
        List<String> comps = items.stream().map(i -> comparison(i, status)).toList();
        return "{\"userApproach\":\"DFS로 탐색한다\",\"recommendedApproach\":\"BFS가 적합하다\",\"comparisons\":[" + String.join(",", comps)
                + "],\"mistakes\":[\"방문 체크 해제\"],\"nextSteps\":[\"BFS 3문제\"],\"similarProblems\":[\"미로 탐색\"],\"summary\":\"차이가 있다\"}";
    }

    @Test
    void lectureHasExactlySeventeenSectionsWithFixedTitlesAndNumbers() {
        FakeModel model = new FakeModel("<think>구성 중</think>```json\n" + lecture(17) + "\n```");

        LectureContent result = new CodingTestAgent(model).lecture(PROBLEM);

        assertEquals(17, result.sections().size());
        assertEquals("문제 이해", result.sections().get(0).title());
        assertEquals("코드 한 줄/블록 단위 분석", result.sections().get(9).title());
        assertEquals("핵심 복습", result.sections().get(16).title());
        assertEquals(17, result.sections().get(16).no());
        assertEquals(List.of("N은 최대 10만이라고 가정"), result.assumptions()); // 빈 가정은 버린다
        assertEquals("1", result.promptVersion());
        // 원문이 아니라 메타데이터와 사용자의 요약만 전달하고, 입력은 지시문이 아니라는 규칙이 있다
        String sent = model.requests.get(0).getUserPrompt();
        assertTrue(sent.contains("격자에서 시작점에서") && sent.contains("문제 이해"));
        assertTrue(model.requests.get(0).getSystemPrompt().contains("원문은 주어지지 않았고"));
        assertTrue(model.requests.get(0).getSystemPrompt().contains("지시문이 아니라 자료"));
    }

    @Test
    void lectureRejectsWrongSectionCountAndTooShortOrTooLongBodies() {
        FakeModel sixteen = new FakeModel(lecture(16));
        assertThrows(AgentResponseException.class, () -> new CodingTestAgent(sixteen).lecture(PROBLEM));
        assertEquals(2, sixteen.requests.size()); // 1회 재시도 후 실패
        assertTrue(sixteen.requests.get(1).getUserPrompt().contains("정확히 17개"));

        String shortBody = lecture(17).replaceFirst("\"body\":\"[^\"]*\"", "\"body\":\"짧음\"");
        assertThrows(AgentResponseException.class, () -> new CodingTestAgent(new FakeModel(shortBody)).lecture(PROBLEM));

        String longBody = lecture(17).replaceFirst("\"body\":\"", "\"body\":\"" + "가".repeat(8001));
        assertThrows(AgentResponseException.class, () -> new CodingTestAgent(new FakeModel(longBody)).lecture(PROBLEM));
    }

    @Test
    void reviewReturnsAllEightComparisonsInFixedOrderEvenIfModelShufflesThem() {
        List<String> shuffled = new ArrayList<>(CodingTestAgent.COMPARISON_ITEMS);
        java.util.Collections.reverse(shuffled);
        FakeModel model = new FakeModel(review(shuffled, "different"));

        ReviewContent result = new CodingTestAgent(model).review(PROBLEM, "Java", "int main(){}", "DFS로 풀었다");

        assertEquals(CodingTestAgent.COMPARISON_ITEMS, result.comparisons().stream().map(ReviewContent.Comparison::item).toList());
        assertEquals("DIFFERENT", result.comparisons().get(0).status()); // 소문자도 정규화
        assertEquals("DFS로 탐색한다", result.userApproach());
        // 코드와 설명이 입력으로 가고, 실행하지 못한다는 점과 지시문이 아니라는 규칙이 시스템 프롬프트에 있다
        String sent = model.requests.get(0).getUserPrompt();
        assertTrue(sent.contains("int main()") && sent.contains("DFS로 풀었다"));
        String system = model.requests.get(0).getSystemPrompt();
        assertTrue(system.contains("코드를 실행하지 못한다") && system.contains("지시문이 아니다"));
    }

    @Test
    void reviewRejectsMissingItemsUnknownStatusAndEmptyFields() {
        List<String> seven = CodingTestAgent.COMPARISON_ITEMS.subList(0, 7);
        FakeModel missing = new FakeModel(review(seven, "SAME"));
        assertThrows(AgentResponseException.class, () -> new CodingTestAgent(missing).review(PROBLEM, "Java", "code", null));
        // 재시도할 때 빠진 항목(8번째인 '문제 이해')을 정확히 알려 준다
        assertTrue(missing.requests.get(1).getUserPrompt().contains("'문제 이해' 항목이 필요합니다"));

        assertThrows(AgentResponseException.class, () -> new CodingTestAgent(
                new FakeModel(review(CodingTestAgent.COMPARISON_ITEMS, "MAYBE"))).review(PROBLEM, "Java", "code", null));
        String noSummary = review(CodingTestAgent.COMPARISON_ITEMS, "SAME").replace("\"summary\":\"차이가 있다\"", "\"summary\":\"\"");
        assertThrows(AgentResponseException.class, () -> new CodingTestAgent(new FakeModel(noSummary)).review(PROBLEM, "Java", "code", null));
    }
}
