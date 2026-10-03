package com.personal.ai.api.learning;

import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Step 진행 상태 전이를 한 곳에 모은다: AVAILABLE/REVIEW_REQUIRED → LEARNING → COMPLETED, 완료하면 다음 Step을 연다.
 * 지금은 학습자가 직접 완료를 표시한다(자가 완료). Step 평가(PASSED/REVIEW_REQUIRED)를 붙이면 complete의 조건만 바뀐다.
 */
@Service
public class StepProgressService {

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

    @Transactional
    public Long complete(Long stepId) {
        LearningStep step = find(stepId);
        if (step.getStatus() != StepStatus.LEARNING) {
            throw conflict(step, "학습 중인 Step만 완료할 수 있습니다");
        }
        step.setStatus(StepStatus.COMPLETED);
        steps.findByGoalIdOrderBySeq(step.getGoal().getId()).stream()
                .filter(s -> s.getSeq() > step.getSeq())
                .findFirst()
                .filter(next -> next.getStatus() == StepStatus.LOCKED)
                .ifPresent(next -> next.setStatus(StepStatus.AVAILABLE));
        return step.getGoal().getId();
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
