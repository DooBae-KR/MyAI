package com.personal.ai.api.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.agent.tutor.LessonContent;
import com.personal.ai.agent.tutor.LessonRequest;
import com.personal.ai.agent.tutor.TutorAgent;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.PatternStatus;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import com.personal.ai.data.learning.ThinkingPatternRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 학습 자료를 만들어 Step의 detail JSON("lesson" 키)에 저장한다. 저장된 것이 있으면 LLM을 다시 부르지 않는다.
 * 진단에서 관찰된 약점과 사고 패턴 가설(기각된 것 제외)을 참고 자료로 넘겨 설명을 개인화한다.
 * LLM 호출 동안은 DB 트랜잭션을 잡지 않는다.
 */
@Service
public class TutorService {

    static final int MAX_PATTERNS = 3;
    static final String LESSON_KEY = "lesson";

    private final LearningStepRepository steps;
    private final AssessmentRepository assessments;
    private final ThinkingPatternRepository patterns;
    private final TutorAgent tutor;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public TutorService(LearningStepRepository steps, AssessmentRepository assessments, ThinkingPatternRepository patterns,
                        TutorAgent tutor, ObjectMapper mapper, TransactionTemplate tx) {
        this.steps = steps;
        this.assessments = assessments;
        this.patterns = patterns;
        this.tutor = tutor;
        this.mapper = mapper;
        this.tx = tx;
    }

    public LessonContent lesson(Long stepId, boolean refresh) {
        LearningStep step = steps.findWithGoalById(stepId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step을 찾을 수 없습니다: " + stepId));
        if (step.getStatus() == StepStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "아직 열리지 않은 Step입니다. 앞선 Step을 먼저 완료하세요.");
        }

        JsonNode detail = readDetail(step.getDetail());
        if (!refresh && detail.hasNonNull(LESSON_KEY)) {
            return read(detail.get(LESSON_KEY));
        }

        Long goalId = step.getGoal().getId();
        List<String> weaknesses = assessments
                .findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(goalId, AssessmentType.DIAGNOSTIC)
                .map(a -> readResult(a.getResult()).weaknesses()).orElse(List.of());
        List<LessonRequest.Pattern> observed = patterns.findAllByOrderByStatusDescConfidenceDescIdAsc().stream()
                .filter(p -> p.getStatus() != PatternStatus.DISMISSED)
                .limit(MAX_PATTERNS)
                .map(p -> new LessonRequest.Pattern(p.getName(), p.getImprovementStrategy())).toList();

        LessonContent lesson = LlmCalls.run(() -> tutor.lesson(new LessonRequest(
                step.getGoal().getSubject().getName(), step.getGoal().getGoalText(), step.getTitle(), step.getObjective(),
                step.getDifficulty() == null ? 2 : step.getDifficulty(), practiceTasks(detail), weaknesses, observed)));

        tx.executeWithoutResult(s -> {
            LearningStep managed = steps.findById(stepId).orElseThrow();
            ObjectNode merged = readDetail(managed.getDetail()).deepCopy();
            merged.set(LESSON_KEY, mapper.valueToTree(lesson));
            managed.setDetail(merged.toString());
        });
        return lesson;
    }

    private static List<String> practiceTasks(JsonNode detail) {
        List<String> tasks = new ArrayList<>();
        detail.path("practiceTasks").forEach(t -> tasks.add(t.asText()));
        return tasks;
    }

    private ObjectNode readDetail(String json) {
        try {
            JsonNode node = json == null ? mapper.createObjectNode() : mapper.readTree(json);
            return node instanceof ObjectNode o ? o : mapper.createObjectNode();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 Step 상세를 읽을 수 없습니다.", e);
        }
    }

    private LessonContent read(JsonNode node) {
        try {
            return mapper.treeToValue(node, LessonContent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 학습 자료를 읽을 수 없습니다.", e);
        }
    }

    private GradingResult readResult(String json) {
        try {
            return mapper.readValue(json, GradingResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 채점 결과를 읽을 수 없습니다.", e);
        }
    }
}
