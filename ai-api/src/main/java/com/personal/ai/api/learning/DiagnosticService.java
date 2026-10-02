package com.personal.ai.api.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.DiagnosticQuiz;
import com.personal.ai.agent.evaluator.EvaluatorAgent;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningGoalRepository;
import com.personal.ai.data.learning.LearningSubject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 목표의 초기 진단 문제를 만들어 저장한다.
 * LLM 호출이 오래 걸릴 수 있어 일부러 @Transactional을 쓰지 않는다. DB 접근은 Repository 호출 단위로만 한다.
 */
@Service
public class DiagnosticService {

    private final LearningGoalRepository goals;
    private final AssessmentRepository assessments;
    private final EvaluatorAgent evaluator;
    private final ObjectMapper mapper;

    public DiagnosticService(LearningGoalRepository goals, AssessmentRepository assessments,
                             EvaluatorAgent evaluator, ObjectMapper mapper) {
        this.goals = goals;
        this.assessments = assessments;
        this.evaluator = evaluator;
        this.mapper = mapper;
    }

    public DiagnosticResponse generate(Long goalId) {
        LearningGoal goal = goals.findWithSubjectById(goalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "목표를 찾을 수 없습니다: " + goalId));
        List<String> prerequisites = goal.getSubject().getPrerequisites().stream()
                .map(LearningSubject::getName).sorted().toList();

        DiagnosticQuiz quiz = LlmCalls.run(() -> evaluator.generateDiagnostic(
                goal.getSubject().getName(), goal.getGoalText(), goal.getTargetLevel(), prerequisites));

        Assessment saved = assessments.save(new Assessment(goal, null, AssessmentType.DIAGNOSTIC, toJson(quiz)));
        return new DiagnosticResponse(saved.getId(), goal.getId(), quiz);
    }

    private String toJson(DiagnosticQuiz quiz) {
        try {
            return mapper.writeValueAsString(quiz);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
