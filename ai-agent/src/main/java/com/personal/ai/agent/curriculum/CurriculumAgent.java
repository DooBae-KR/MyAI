package com.personal.ai.agent.curriculum;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.Json;
import com.personal.ai.agent.Prompt;
import com.personal.ai.agent.StructuredLlm;
import com.personal.ai.core.model.AiModel;

import java.util.ArrayList;
import java.util.List;

/** 진단 결과로 개인 커리큘럼(Step 목록)을 만든다. DB를 모른다. */
public class CurriculumAgent {

    private static final int MIN_STEPS = 3;
    private static final int MAX_STEPS = 20;

    private final StructuredLlm llm;
    private final Prompt prompt = Prompt.load("curriculum-agent");

    public CurriculumAgent(AiModel model) {
        this.llm = new StructuredLlm(model);
    }

    public CurriculumPlan plan(CurriculumRequest request) {
        List<PlannedStep> steps = llm.call(prompt, llm.toJson(request), 0.4, this::parseSteps);
        return new CurriculumPlan(prompt.version(), steps);
    }

    List<PlannedStep> parseSteps(JsonNode root) {
        JsonNode items = root.path("steps");
        if (!items.isArray() || items.size() < MIN_STEPS || items.size() > MAX_STEPS) {
            throw new AgentResponseException("steps는 " + MIN_STEPS + "~" + MAX_STEPS + "개의 배열이어야 합니다.");
        }

        List<PlannedStep> steps = new ArrayList<>();
        for (JsonNode item : items) {
            int seq = steps.size() + 1; // 순서는 모델이 아니라 우리가 매긴다
            String label = "Step " + seq;
            String title = Json.text(item, "title").trim();
            String objective = Json.text(item, "objective").trim();
            if (title.isBlank() || objective.isBlank()) {
                throw new AgentResponseException(label + ": title과 objective는 필수입니다.");
            }
            int difficulty = Json.intInRange(item, "difficulty", 1, 5, label);
            int minutes = Json.intInRange(item, "estimatedMinutes", 10, 6000, label);
            List<String> tasks = Json.strings(item, "practiceTasks", 5);
            if (tasks.isEmpty()) {
                throw new AgentResponseException(label + ": practiceTasks에 실습 과제가 1개 이상 필요합니다.");
            }
            steps.add(new PlannedStep(seq, title, objective, difficulty, minutes, tasks));
        }
        return steps;
    }
}
