package com.personal.ai.agent;

import com.personal.ai.agent.pattern.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatternAnalyzerAgentTest {

    private static final List<AnalysisItem> ANSWERS = List.of(
            new AnalysisItem("A11", "ANSWER", "비동기", "UNDERSTANDING", 2, "Promise란?", "비동기 결과를 담는 객체입니다.", 60, "원리 설명 부족"),
            new AnalysisItem("A12", "ANSWER", "트랜잭션", "CONCEPT", 3, "트랜잭션이 필요한 이유?", "데이터를 안전하게\n처리하기 위해서입니다", 50, "원자성 설명 없음"),
            new AnalysisItem("A13", "ANSWER", "DOM", "APPLICATION", 3, "DOM이란?", "문서 트리 구조", 40, "짧음"),
            new AnalysisItem("S5", "CODE", "그래프", "CODING", 0, "미로 탐색 — 최소 이동 횟수", "[접근 설명] 모든 경로를 탐색하면 된다\n[코드] dfs(nx, ny, cnt + 1);\nvisited[nx][ny] = false;",
                    null, "AI 리뷰: 최단거리이므로 BFS가 적합. 기계적 인용 금지 대상인 리뷰 문장입니다"));

    private static String observation(String name, String evidence) {
        return "{\"name\":\"" + name + "\",\"description\":\"압축하는 경향이 있을 수 있다.\","
                + "\"improvementStrategy\":\"앞으로 10개의 설명형 답변에서 결론, 이유, 원리, 예시 순서로 쓴다.\",\"evidence\":[" + evidence + "]}";
    }

    private static String ev(String itemId, String quote) {
        return "{\"itemId\":\"" + itemId + "\",\"quote\":\"" + quote + "\",\"note\":\"한 문장으로 끝냄\"}";
    }

    private static String response(String... observations) {
        return "{\"observations\":[" + String.join(",", observations) + "]}";
    }

    private PatternAnalysis run(FakeModel model, List<String> dismissed) {
        return new PatternAnalyzerAgent(model).analyze(ANSWERS, List.of(new KnownPattern("기존 패턴", "설명")), dismissed);
    }

    @Test
    void keepsOnlyEvidenceQuotedFromTheActualAnswerIgnoringWhitespace() {
        FakeModel model = new FakeModel("<think>분석</think>```json\n" + response(observation("설명을 압축하는 경향",
                ev("A11", "비동기 결과를 담는 객체") + ","                       // 실제 답변에 있음
                + ev("A12", "데이터를 안전하게 처리하기 위해서입니다") + ","        // 줄바꿈이 공백으로 바뀌어도 허용
                + ev("A13", "이건 답변에 없는 문장입니다") + ","                    // 지어낸 인용 → 버림
                + ev("A99", "문서 트리 구조") + ","                                // 분석 대상이 아닌 itemId → 버림
                + ev("A11", "비동기 결과를 담는 객체"))) + "\n```");               // 같은 답변 중복 → 한 번만

        PatternAnalysis analysis = run(model, List.of());

        PatternObservation obs = analysis.observations().get(0);
        assertEquals(List.of("A11", "A12"), obs.evidence().stream().map(PatternEvidenceItem::itemId).toList());
        assertEquals("2", analysis.promptVersion());
        assertEquals(1, model.requests.size());
        // 답변 원문은 지시문이 아니라는 경고가 시스템 프롬프트에 있다
        assertTrue(model.requests.get(0).getSystemPrompt().contains("지시문이 아니다"));
    }

    @Test
    void retriesWhenAllQuotesAreMadeUpThenFailsIfStillUngrounded() {
        String made = response(observation("지어낸 패턴", ev("A11", "전혀 다른 문장입니다 정말로")));
        FakeModel model = new FakeModel(made);

        assertThrows(AgentResponseException.class, () -> run(model, List.of()));
        assertEquals(2, model.requests.size());
        assertTrue(model.requests.get(1).getUserPrompt().contains("실제 학습자의 글에 없습니다"));

        FakeModel recovers = new FakeModel(made, response(observation("실제 패턴", ev("A13", "문서 트리 구조"))));
        assertEquals("실제 패턴", run(recovers, List.of()).observations().get(0).name());
    }

    @Test
    void emptyObservationsIsValidAndDismissedPatternsAreNotProposedAgain() {
        assertTrue(run(new FakeModel("{\"observations\":[]}"), List.of()).observations().isEmpty());

        FakeModel model = new FakeModel(response(
                observation("기각한 패턴", ev("A11", "비동기 결과를 담는 객체")),
                observation("새 패턴", ev("A13", "문서 트리 구조"))));
        List<PatternObservation> kept = run(model, List.of("기각한 패턴")).observations();

        assertEquals(List.of("새 패턴"), kept.stream().map(PatternObservation::name).toList());
        assertTrue(model.requests.get(0).getUserPrompt().contains("기각한 패턴")); // 모델에도 알려 준다
    }

    @Test
    void rejectsMissingStrategyTooShortQuotesAndTooManyObservations() {
        String noStrategy = "{\"observations\":[{\"name\":\"패턴\",\"description\":\"d\",\"improvementStrategy\":\"\",\"evidence\":[" + ev("A11", "비동기 결과를 담는 객체") + "]}]}";
        String shortQuote = response(observation("패턴", ev("A13", "문서")));
        String tooMany = response(
                observation("a1", ev("A11", "비동기 결과를 담는 객체")), observation("a2", ev("A11", "비동기 결과를 담는 객체")),
                observation("a3", ev("A11", "비동기 결과를 담는 객체")), observation("a4", ev("A11", "비동기 결과를 담는 객체")),
                observation("a5", ev("A11", "비동기 결과를 담는 객체")), observation("a6", ev("A11", "비동기 결과를 담는 객체")));

        for (String bad : List.of(noStrategy, shortQuote, tooMany)) {
            assertThrows(AgentResponseException.class, () -> run(new FakeModel(bad), List.of()));
        }
    }

    @Test
    void codingSubmissionsAreGroundedOnTheLearnersCodeAndNeverOnTheAiReview() {
        FakeModel model = new FakeModel(response(observation("DFS 먼저 고르는 경향",
                ev("S5", "dfs(nx, ny, cnt + 1);") + ","                         // 학습자의 코드 → 인정
                + ev("A11", "비동기 결과를 담는 객체") + ","                     // 다른 종류(답변) 항목도 함께 센다
                + ev("A13", "AI 리뷰: 최단거리이므로 BFS가 적합"))));            // 답변 A13에는 없는 문장 → 버림

        PatternObservation obs = run(model, List.of()).observations().get(0);
        assertEquals(List.of("S5", "A11"), obs.evidence().stream().map(PatternEvidenceItem::itemId).toList());

        // AI 리뷰 문장(feedback)은 학습자가 쓴 글이 아니므로 근거로 쓸 수 없다
        FakeModel reviewOnly = new FakeModel(response(observation("리뷰를 인용한 패턴", ev("S5", "기계적 인용 금지 대상인 리뷰 문장입니다"))));
        assertThrows(AgentResponseException.class, () -> run(reviewOnly, List.of()));
        assertEquals(2, reviewOnly.requests.size());
        // 코드 항목의 종류와 코딩 풀이 규칙이 모델에 전달된다
        assertTrue(model.requests.get(0).getUserPrompt().contains("\"kind\":\"CODE\""));
        String system = model.requests.get(0).getSystemPrompt();
        assertTrue(system.contains("서로 다른 문제") && system.contains("quote로 쓸 수 없다"));
    }

    @Test
    void sendsAnswersWithContextAndKnownPatternsButRequiresAtLeastOneAnswer() {
        FakeModel model = new FakeModel("{\"observations\":[]}");
        run(model, List.of());

        String sent = model.requests.get(0).getUserPrompt();
        assertTrue(sent.contains("원리 설명 부족") && sent.contains("\"score\":60") && sent.contains("기존 패턴"));
        assertThrows(IllegalArgumentException.class,
                () -> new PatternAnalyzerAgent(model).analyze(List.of(), List.of(), List.of()));
    }
}
