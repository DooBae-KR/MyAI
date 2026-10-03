package com.personal.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.personal.ai.agent.tutor.LessonContent;
import com.personal.ai.agent.tutor.LessonRequest;
import com.personal.ai.agent.tutor.TutorAgent;
import java.util.List;
import org.junit.jupiter.api.Test;

class TutorAgentTest {

    private static final String LONG = "충분히 긴 설명 문장입니다. 이유와 동작 원리를 함께 설명합니다.";

    private static String lesson(String checklist) {
        return "{\"overview\":\"" + LONG + "\",\"concepts\":[{\"title\":\"Promise\",\"explanation\":\"" + LONG
                + "\"},{\"title\":\"async\",\"explanation\":\"" + LONG + "\"}],\"examples\":[{\"title\":\"fetch\",\"language\":\"javascript\","
                + "\"code\":\"const r = await fetch(url)\",\"explanation\":\"" + LONG + "\"}],\"implementationTask\":\"" + LONG
                + "\",\"commonMistakes\":[\"await 누락\"],\"checklist\":" + checklist + "}";
    }

    private static final String VALID = lesson("[\"a를 할 수 있다\",\"b를 할 수 있다\",\"c를 할 수 있다\"]");

    private static final LessonRequest REQUEST = new LessonRequest("Vue", "실무", "비동기와 Promise", "Promise를 쓴다", 2,
            List.of("fetch 과제"), List.of("비동기 흐름 설명이 부족함"),
            List.of(new LessonRequest.Pattern("설명 압축 경향", "이유를 한 문장 더 쓰기")));

    @Test
    void parsesLessonAndSendsPersonalizationContext() {
        FakeModel model = new FakeModel("```json\n" + VALID + "\n```");

        LessonContent lesson = new TutorAgent(model).lesson(REQUEST);

        assertEquals("1", lesson.promptVersion());
        assertEquals(2, lesson.concepts().size());
        assertEquals("const r = await fetch(url)", lesson.examples().get(0).code());
        assertEquals(3, lesson.checklist().size());
        String user = model.requests.get(0).getUserPrompt();
        assertTrue(user.contains("비동기 흐름 설명이 부족함") && user.contains("설명 압축 경향") && user.contains("fetch 과제"));
        assertTrue(model.requests.get(0).getSystemPrompt().contains("Tutor Agent"));
    }

    @Test
    void retriesOnceWhenChecklistIsTooShort() {
        FakeModel model = new FakeModel(lesson("[\"하나뿐\"]"), VALID);

        new TutorAgent(model).lesson(REQUEST);

        assertEquals(2, model.requests.size());
        assertTrue(model.requests.get(1).getUserPrompt().contains("checklist"));
    }

    @Test
    void rejectsLessonWithoutExamplesAfterRetry() {
        String noExamples = VALID.replaceAll("\"examples\":\\[.*?\\}\\],", "\"examples\":[],");
        FakeModel model = new FakeModel(noExamples, noExamples);

        assertThrows(AgentResponseException.class, () -> new TutorAgent(model).lesson(REQUEST));
    }
}
