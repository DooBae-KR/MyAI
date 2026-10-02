package com.personal.ai.agent;

import com.personal.ai.agent.evaluator.DiagnosticQuiz;
import com.personal.ai.agent.evaluator.EvaluatorAgent;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorAgentTest {

    private static String q(String type, int difficulty) {
        return "{\"area\":\"JS\",\"type\":\"" + type + "\",\"difficulty\":" + difficulty
                + ",\"question\":\"Promise란?\",\"keyPoints\":[\"비동기\"]}";
    }

    private static final String VALID = "{\"questions\":[" + q("CONCEPT", 1) + "," + q("syntax", 2) + "," + q("DEBUGGING", 3) + "]}";

    /** 준비된 응답을 순서대로 돌려주고, 받은 요청을 기록한다. */
    private static class FakeModel implements AiModel {
        final List<String> responses;
        final List<AiRequest> requests = new ArrayList<>();

        FakeModel(String... responses) { this.responses = List.of(responses); }

        @Override
        public AiResponse chat(AiRequest request) {
            requests.add(request);
            return new AiResponse(responses.get(Math.min(requests.size(), responses.size()) - 1));
        }
    }

    private DiagnosticQuiz run(FakeModel model) {
        return new EvaluatorAgent(model).generateDiagnostic("Vue", "실무", Level.INTERMEDIATE, List.of("JavaScript"));
    }

    @Test
    void parsesFencedThinkingResponseAndNumbersQuestions() {
        FakeModel model = new FakeModel("<think>고민 중</think>\n```json\n" + VALID + "\n```");

        DiagnosticQuiz quiz = run(model);

        assertEquals("1", quiz.promptVersion());
        assertEquals(List.of(1, 2, 3), quiz.questions().stream().map(x -> x.id()).toList());
        assertEquals("SYNTAX", quiz.questions().get(1).type()); // 소문자 type도 정규화
        assertEquals(1, model.requests.size());
        assertTrue(model.requests.get(0).getUserPrompt().contains("JavaScript"));
        assertTrue(model.requests.get(0).getSystemPrompt().contains("진단 문제"));
        assertFalse(model.requests.get(0).getSystemPrompt().contains("promptVersion"));
    }

    @Test
    void retriesOnceWithErrorNoteThenSucceeds() {
        FakeModel model = new FakeModel("죄송합니다, 문제를 만들 수 없어요", VALID);

        assertEquals(3, run(model).questions().size());
        assertEquals(2, model.requests.size());
        assertTrue(model.requests.get(1).getUserPrompt().contains("[이전 응답 오류]"));
    }

    @Test
    void failsAfterSecondInvalidResponse() {
        FakeModel model = new FakeModel("not json", "{\"questions\":[]}");

        assertThrows(AgentResponseException.class, () -> run(model));
        assertEquals(2, model.requests.size());
    }

    @Test
    void rejectsOutOfRangeDifficultyAndUnknownType() {
        String badDifficulty = "{\"questions\":[" + q("CONCEPT", 9) + "," + q("CONCEPT", 1) + "," + q("CONCEPT", 1) + "]}";
        String badType = "{\"questions\":[" + q("TRIVIA", 1) + "," + q("CONCEPT", 1) + "," + q("CONCEPT", 1) + "]}";

        assertThrows(AgentResponseException.class, () -> run(new FakeModel(badDifficulty)));
        assertThrows(AgentResponseException.class, () -> run(new FakeModel(badType)));
    }

    @Test
    void parsesPromptFrontMatter() {
        Prompt prompt = Prompt.parse("---\npromptVersion: 7\n---\n본문\n");

        assertEquals("7", prompt.version());
        assertEquals("본문", prompt.text());
    }
}
