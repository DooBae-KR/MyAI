package com.personal.ai.api.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.DiagnosticQuiz;
import com.personal.ai.agent.evaluator.EvaluatorAgent;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Step 확인 문제를 만든다. 풀다 만 문제가 있으면 새로 만들지 않고(LLM 비용) 그대로 돌려준다.
 * LLM 호출 동안은 DB 트랜잭션을 잡지 않는다.
 */
@Service
public class StepAssessmentService {

    private final LearningStepRepository steps;
    private final AssessmentRepository assessments;
    private final StepProgressService progress;
    private final EvaluatorAgent evaluator;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public StepAssessmentService(LearningStepRepository steps, AssessmentRepository assessments,
                                 StepProgressService progress, EvaluatorAgent evaluator,
                                 ObjectMapper mapper, TransactionTemplate tx) {
        this.steps = steps;
        this.assessments = assessments;
        this.progress = progress;
        this.evaluator = evaluator;
        this.mapper = mapper;
        this.tx = tx;
    }

    public DiagnosticResponse open(Long stepId) {
        LearningStep step = steps.findWithGoalById(stepId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step을 찾을 수 없습니다: " + stepId));
        Long goalId = step.getGoal().getId();

        Optional<Assessment> pending = assessments.findFirstByStepIdAndTypeOrderByIdDesc(stepId, AssessmentType.STEP)
                .filter(a -> a.getResult() == null);
        if (pending.isPresent()) {
            tx.executeWithoutResult(s -> progress.markAssessing(steps.findById(stepId).orElseThrow()));
            return new DiagnosticResponse(pending.get().getId(), goalId, QuizView.from(read(pending.get().getQuestions())));
        }

        progress.checkAssessable(step); // LLM을 부르기 전에 상태부터 검사한다(409를 빨리)
        DiagnosticQuiz quiz = LlmCalls.run(() -> evaluator.generateStepQuiz(
                step.getGoal().getSubject().getName(), step.getGoal().getGoalText(), step.getTitle(),
                step.getObjective(), step.getDifficulty() == null ? 2 : step.getDifficulty(), practiceTasks(step)));

        Assessment saved = tx.execute(s -> {
            LearningStep managed = steps.findById(stepId).orElseThrow();
            progress.markAssessing(managed);
            return assessments.save(new Assessment(managed.getGoal(), managed, AssessmentType.STEP, write(quiz)));
        });
        return new DiagnosticResponse(saved.getId(), goalId, QuizView.from(quiz));
    }

    private List<String> practiceTasks(LearningStep step) {
        try {
            List<String> tasks = new java.util.ArrayList<>();
            if (step.getDetail() != null) mapper.readTree(step.getDetail()).path("practiceTasks").forEach(t -> tasks.add(t.asText()));
            return tasks;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 Step 상세를 읽을 수 없습니다.", e);
        }
    }

    private DiagnosticQuiz read(String json) {
        try {
            return mapper.readValue(json, DiagnosticQuiz.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 확인 문제를 읽을 수 없습니다.", e);
        }
    }

    private String write(DiagnosticQuiz quiz) {
        try {
            return mapper.writeValueAsString(quiz);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
