package com.personal.ai.agent.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.Json;
import com.personal.ai.agent.Prompt;
import com.personal.ai.agent.StructuredLlm;
import com.personal.ai.core.model.AiModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Step 학습 자료(개념, 예제, 구현 과제, 완료 체크) 생성. DB를 모르고, 응답은 검증을 통과한 것만 돌려준다. */
public class TutorAgent {

    private static final int MIN_TEXT = 20;
    private static final int MAX_TEXT = 3000;
    private static final int MAX_CODE = 3000;

    private final StructuredLlm llm;
    private final Prompt prompt = Prompt.load("tutor-agent");

    public TutorAgent(AiModel model) {
        this.llm = new StructuredLlm(model);
    }

    public LessonContent lesson(LessonRequest request) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("title", request.stepTitle());
        step.put("objective", request.objective());
        step.put("difficulty", request.difficulty());
        step.put("practiceTasks", request.practiceTasks());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("subject", request.subject());
        input.put("goal", request.goal());
        input.put("step", step);
        input.put("learnerWeaknesses", request.learnerWeaknesses());
        input.put("observedPatterns", request.observedPatterns());
        return llm.call(prompt, llm.toJson(input), 0.4, this::parse);
    }

    LessonContent parse(JsonNode root) {
        String overview = text(root, "overview", "overview");

        JsonNode concepts = root.path("concepts");
        if (!concepts.isArray() || concepts.size() < 2 || concepts.size() > 5) {
            throw new AgentResponseException("concepts는 2~5개의 배열이어야 합니다.");
        }
        List<LessonContent.Concept> conceptList = new ArrayList<>();
        for (JsonNode c : concepts) {
            String label = "개념 " + (conceptList.size() + 1);
            conceptList.add(new LessonContent.Concept(shortText(c, "title", label), text(c, "explanation", label + "의 explanation")));
        }

        JsonNode examples = root.path("examples");
        if (!examples.isArray() || examples.isEmpty() || examples.size() > 3) {
            throw new AgentResponseException("examples는 1~3개의 배열이어야 합니다.");
        }
        List<LessonContent.Example> exampleList = new ArrayList<>();
        for (JsonNode e : examples) {
            String label = "예제 " + (exampleList.size() + 1);
            String code = Json.text(e, "code").strip();
            if (code.isEmpty() || code.length() > MAX_CODE) {
                throw new AgentResponseException(label + ": code는 1~" + MAX_CODE + "자여야 합니다.");
            }
            exampleList.add(new LessonContent.Example(shortText(e, "title", label), Json.text(e, "language").trim(), code,
                    text(e, "explanation", label + "의 explanation")));
        }

        List<String> checklist = Json.strings(root, "checklist", 6);
        if (checklist.size() < 3) {
            throw new AgentResponseException("checklist는 3~6개여야 합니다.");
        }
        return new LessonContent(prompt.version(), overview, conceptList, exampleList,
                text(root, "implementationTask", "implementationTask"), Json.strings(root, "commonMistakes", 5), checklist);
    }

    private static String text(JsonNode node, String field, String label) {
        String value = Json.text(node, field).trim();
        if (value.length() < MIN_TEXT) {
            throw new AgentResponseException(label + "이(가) 비었거나 너무 짧습니다(" + MIN_TEXT + "자 이상).");
        }
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }

    private static String shortText(JsonNode node, String field, String label) {
        String value = Json.text(node, field).trim();
        if (value.isEmpty()) {
            throw new AgentResponseException(label + "의 " + field + "가 비어 있습니다.");
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }
}
