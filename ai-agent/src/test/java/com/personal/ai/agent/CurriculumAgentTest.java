package com.personal.ai.agent;

import com.personal.ai.agent.curriculum.CurriculumAgent;
import com.personal.ai.agent.curriculum.CurriculumPlan;
import com.personal.ai.agent.curriculum.CurriculumRequest;
import com.personal.ai.agent.evaluator.AreaScore;
import com.personal.ai.core.learning.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurriculumAgentTest {

    private static String step(String title, int difficulty, String minutes, String tasks) {
        return "{\"title\":\"" + title + "\",\"objective\":\"목표\",\"difficulty\":" + difficulty
                + ",\"estimatedMinutes\":" + minutes + ",\"practiceTasks\":" + tasks + "}";
    }

    private static final String VALID = "{\"steps\":[" + step("비동기", 2, "120", "[\"fetch 실습\"]") + ","
            + step("컴포넌트", 3, "90", "[\"버튼 만들기\", \"\"]") + "," + step("프로젝트", 4, "300", "[\"앱 만들기\"]") + "]}";

    private static final CurriculumRequest REQUEST = new CurriculumRequest("Vue", "실무", Level.INTERMEDIATE,
            Level.BEGINNER, List.of("JavaScript"), List.of(new AreaScore("비동기", 30)), List.of("개념"), List.of("설명 부족"));

    @Test
    void parsesStepsNumbersThemAndSendsDiagnosisToModel() {
        FakeModel model = new FakeModel("<think>설계 중</think>\n```json\n" + VALID + "\n```");

        CurriculumPlan plan = new CurriculumAgent(model).plan(REQUEST);

        assertEquals("1", plan.promptVersion());
        assertEquals(List.of(1, 2, 3), plan.steps().stream().map(s -> s.seq()).toList());
        assertEquals(List.of("버튼 만들기"), plan.steps().get(1).practiceTasks()); // 빈 과제는 버린다
        String sent = model.requests.get(0).getUserPrompt();
        assertTrue(sent.contains("\"currentLevel\":\"BEGINNER\"") && sent.contains("설명 부족") && sent.contains("\"score\":30"));
        assertTrue(model.requests.get(0).getSystemPrompt().contains("개인 맞춤 커리큘럼"));
    }

    @Test
    void retriesOnceThenFailsOnInvalidSteps() {
        String tooFew = "{\"steps\":[" + step("하나", 1, "30", "[\"a\"]") + "]}";
        String noTasks = "{\"steps\":[" + step("a", 1, "30", "[]") + "," + step("b", 1, "30", "[\"x\"]") + ","
                + step("c", 1, "30", "[\"x\"]") + "]}";
        String badMinutes = "{\"steps\":[" + step("a", 1, "5", "[\"x\"]") + "," + step("b", 1, "30", "[\"x\"]") + ","
                + step("c", 1, "30", "[\"x\"]") + "]}";

        for (String bad : List.of(tooFew, noTasks, badMinutes)) {
            FakeModel model = new FakeModel(bad);
            assertThrows(AgentResponseException.class, () -> new CurriculumAgent(model).plan(REQUEST));
            assertEquals(2, model.requests.size());
        }
    }

    @Test
    void recoversWhenSecondAttemptIsValid() {
        FakeModel model = new FakeModel("커리큘럼은 다음과 같습니다", VALID);

        assertEquals(3, new CurriculumAgent(model).plan(REQUEST).steps().size());
        assertTrue(model.requests.get(1).getUserPrompt().contains("[이전 응답 오류]"));
    }
}
