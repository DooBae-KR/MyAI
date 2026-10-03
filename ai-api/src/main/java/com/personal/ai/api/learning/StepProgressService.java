package com.personal.ai.api.learning;

import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Step 진행 상태 전이를 한 곳에 모은다.
 * AVAILABLE/REVIEW_REQUIRED → LEARNING → ASSESSMENT(확인 문제) → 합격 COMPLETED(다음 Step 열림) / 불합격 REVIEW_REQUIRED.
 */
@Service
public class StepProgressService {

    /** 확인 문제 합격선(난이도 가중 정답률). */
    public static final int PASS_SCORE = 70;

    private final LearningStepRepository steps;
    private final DashboardService dashboard;

    public StepProgressService(LearningStepRepository steps, DashboardService dashboard) {
        this.steps = steps;
        this.dashboard = dashboard;
    }

    @Transactional
    public Long start(Long stepId) {
        LearningStep step = find(stepId);
        if (step.getStatus() != StepStatus.AVAILABLE && step.getStatus() != StepStatus.REVIEW_REQUIRED) {
            throw conflict(step, "시작할 수 있는 상태가 아닙니다");
        }
        step.setStatus(StepStatus.LEARNING);
        return step.getGoal().getId();
    }

    /** 확인 문제를 풀 수 있는 상태(LEARNING, 또는 이어 풀기인 ASSESSMENT)인지 검사한다. 상태는 바꾸지 않는다. */
    public void checkAssessable(LearningStep step) {
        if (step.getStatus() != StepStatus.LEARNING && step.getStatus() != StepStatus.ASSESSMENT) {
            throw conflict(step, "학습 중인 Step만 확인 문제를 풀 수 있습니다");
        }
    }

    /** 검사 후 ASSESSMENT로 바꾼다. 호출하는 쪽의 트랜잭션 안에서 실행한다. */
    public void markAssessing(LearningStep step) {
        checkAssessable(step);
        step.setStatus(StepStatus.ASSESSMENT);
    }

    /** 채점 결과를 Step에 반영한다. 호출하는 쪽의 트랜잭션 안에서 실행한다. */
    public StepOutcome applyAssessment(LearningStep step, int correctness) {
        boolean passed = correctness >= PASS_SCORE;
        step.setStatus(passed ? StepStatus.COMPLETED : StepStatus.REVIEW_REQUIRED);
        if (passed) {
            steps.findByGoalIdOrderBySeq(step.getGoal().getId()).stream()
                    .filter(s -> s.getSeq() > step.getSeq())
                    .findFirst()
                    .filter(next -> next.getStatus() == StepStatus.LOCKED)
                    .ifPresent(next -> next.setStatus(StepStatus.AVAILABLE));
        }
        return new StepOutcome(passed, PASS_SCORE, step.getStatus());
    }

    public GoalDetail goal(Long goalId) {
        return dashboard.goal(goalId);
    }

    private LearningStep find(Long stepId) {
        return steps.findById(stepId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step을 찾을 수 없습니다: " + stepId));
    }

    private static ResponseStatusException conflict(LearningStep step, String why) {
        return new ResponseStatusException(HttpStatus.CONFLICT, why + " (현재 상태: " + step.getStatus() + ")");
    }
}
