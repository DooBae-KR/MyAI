package com.personal.ai.api.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.curriculum.CurriculumAgent;
import com.personal.ai.agent.curriculum.CurriculumPlan;
import com.personal.ai.agent.curriculum.CurriculumRequest;
import com.personal.ai.agent.curriculum.PlannedStep;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningGoalRepository;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import com.personal.ai.data.learning.LearningSubject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 채점된 진단 결과로 목표의 개인 커리큘럼을 만든다. 목표당 한 번만 만들 수 있다.
 * 첫 Step만 AVAILABLE, 나머지는 LOCKED로 시작한다.
 */
@Service
public class CurriculumService {

    private final LearningGoalRepository goals;
    private final AssessmentRepository assessments;
    private final LearningStepRepository steps;
    private final CurriculumAgent curriculumAgent;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public CurriculumService(LearningGoalRepository goals, AssessmentRepository assessments,
                             LearningStepRepository steps, CurriculumAgent curriculumAgent,
                             ObjectMapper mapper, TransactionTemplate tx) {
        this.goals = goals;
        this.assessments = assessments;
        this.steps = steps;
        this.curriculumAgent = curriculumAgent;
        this.mapper = mapper;
        this.tx = tx;
    }

    public CurriculumResponse create(Long goalId) {
        LearningGoal goal = goals.findWithSubjectById(goalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "목표를 찾을 수 없습니다: " + goalId));
        if (steps.existsByGoalId(goalId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 커리큘럼이 있는 목표입니다.");
        }
        Assessment diagnostic = assessments
                .findFirstByGoalIdAndTypeAndResultIsNotNullOrderByIdDesc(goalId, AssessmentType.DIAGNOSTIC)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "채점된 진단이 없습니다. 진단 답변을 먼저 제출하세요."));
        GradingResult grading = read(diagnostic.getResult());

        CurriculumRequest input = new CurriculumRequest(goal.getSubject().getName(), goal.getGoalText(),
                goal.getTargetLevel(), grading.level(),
                goal.getSubject().getPrerequisites().stream().map(LearningSubject::getName).sorted().toList(),
                grading.areaScores(), grading.strengths(), grading.weaknesses());
        CurriculumPlan plan = LlmCalls.run(() -> curriculumAgent.plan(input));

        List<LearningStep> saved;
        try {
            saved = tx.execute(status -> steps.saveAll(plan.steps().stream()
                    .map(s -> toEntity(goal, s)).toList()));
        } catch (DataIntegrityViolationException e) { // 동시에 두 번 생성된 경우 (goal_id, seq 유일 제약)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 커리큘럼이 있는 목표입니다.");
        }

        List<CurriculumResponse.Step> view = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            PlannedStep p = plan.steps().get(i);
            LearningStep s = saved.get(i);
            view.add(new CurriculumResponse.Step(s.getId(), s.getSeq(), s.getTitle(), s.getObjective(),
                    p.difficulty(), p.estimatedMinutes(), p.practiceTasks(), s.getStatus()));
        }
        return new CurriculumResponse(goalId, plan.promptVersion(), view);
    }

    private LearningStep toEntity(LearningGoal goal, PlannedStep p) {
        String detail = write(Map.of("estimatedMinutes", p.estimatedMinutes(), "practiceTasks", p.practiceTasks()));
        StepStatus status = p.seq() == 1 ? StepStatus.AVAILABLE : StepStatus.LOCKED;
        return new LearningStep(goal, p.seq(), p.title(), p.objective(), p.difficulty(), status, detail);
    }

    private GradingResult read(String json) {
        try {
            return mapper.readValue(json, GradingResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 채점 결과를 읽을 수 없습니다.", e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
