package com.personal.ai.api.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningGoalRepository;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dashboard용 조회. LLM을 호출하지 않으므로 읽기 전용 트랜잭션을 쓴다. */
@Service
public class DashboardService {

    private final LearningGoalRepository goals;
    private final LearningStepRepository steps;
    private final AssessmentRepository assessments;
    private final ObjectMapper mapper;

    public DashboardService(LearningGoalRepository goals, LearningStepRepository steps,
                            AssessmentRepository assessments, ObjectMapper mapper) {
        this.goals = goals;
        this.steps = steps;
        this.assessments = assessments;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<GoalSummary> goals() {
        Map<Long, Map<StepStatus, Integer>> counts = new HashMap<>();
        for (Object[] row : steps.countByGoalAndStatus()) {
            counts.computeIfAbsent((Long) row[0], k -> new EnumMap<>(StepStatus.class))
                    .put((StepStatus) row[1], ((Number) row[2]).intValue());
        }
        return goals.findAllByOrderByIdDesc().stream()
                .map(g -> summary(g, counts.getOrDefault(g.getId(), Map.of()))).toList();
    }

    @Transactional(readOnly = true)
    public GoalDetail goal(Long goalId) {
        LearningGoal goal = goals.findById(goalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "목표를 찾을 수 없습니다: " + goalId));
        List<LearningStep> stepEntities = steps.findByGoalIdOrderBySeq(goalId);

        Map<StepStatus, Integer> counts = new EnumMap<>(StepStatus.class);
        stepEntities.forEach(s -> counts.merge(s.getStatus(), 1, Integer::sum));

        Optional<Assessment> latest = assessments.findFirstByGoalIdAndTypeOrderByIdDesc(goalId, AssessmentType.DIAGNOSTIC);
        Optional<Assessment> graded = assessments
                .findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(goalId, AssessmentType.DIAGNOSTIC);

        String nextAction;
        Long pending = null;
        if (!stepEntities.isEmpty()) {
            nextAction = "LEARNING";
        } else if (graded.isPresent()) {
            nextAction = "CURRICULUM_NEEDED";
        } else if (latest.isPresent()) {
            nextAction = "ANSWERS_NEEDED";
            pending = latest.get().getId();
        } else {
            nextAction = "DIAGNOSTIC_NEEDED";
        }

        GoalDetail.Diagnostic diagnostic = graded.map(a -> {
            GradingResult r = readResult(a.getResult());
            return new GoalDetail.Diagnostic(a.getId(), r.correctness(), r.level(), r.areaScores(),
                    r.strengths(), r.weaknesses());
        }).orElse(null);

        List<CurriculumResponse.Step> view = new ArrayList<>();
        for (LearningStep s : stepEntities) {
            JsonNode detail = readDetail(s.getDetail());
            List<String> tasks = new ArrayList<>();
            detail.path("practiceTasks").forEach(t -> tasks.add(t.asText()));
            view.add(new CurriculumResponse.Step(s.getId(), s.getSeq(), s.getTitle(), s.getObjective(),
                    s.getDifficulty() == null ? 0 : s.getDifficulty(), detail.path("estimatedMinutes").asInt(0),
                    tasks, s.getStatus()));
        }
        return new GoalDetail(summary(goal, counts), nextAction, pending, diagnostic, view);
    }

    private GoalSummary summary(LearningGoal g, Map<StepStatus, Integer> counts) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int done = counts.getOrDefault(StepStatus.COMPLETED, 0);
        int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        return new GoalSummary(g.getId(), g.getSubject().getName(), g.getGoalText(), g.getTargetLevel(),
                g.getCurrentLevel(), g.getDeadline(), total, done, percent);
    }

    private GradingResult readResult(String json) {
        try {
            return mapper.readValue(json, GradingResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 채점 결과를 읽을 수 없습니다.", e);
        }
    }

    private JsonNode readDetail(String json) {
        try {
            return json == null ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 Step 상세를 읽을 수 없습니다.", e);
        }
    }
}
