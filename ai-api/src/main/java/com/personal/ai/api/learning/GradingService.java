package com.personal.ai.api.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.DiagnosticQuestion;
import com.personal.ai.agent.evaluator.DiagnosticQuiz;
import com.personal.ai.agent.evaluator.EvaluatorAgent;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.agent.evaluator.QuestionResult;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.learning.LearningAnswer;
import com.personal.ai.data.learning.LearningAnswerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 진단 답변을 받아 채점하고 결과를 저장한다. 현재 수준(goal.currentLevel)도 갱신한다.
 * LLM 호출 동안은 DB 트랜잭션을 잡지 않고, 저장만 하나의 트랜잭션으로 묶는다.
 */
@Service
public class GradingService {

    static final int MAX_ANSWER_CHARS = 4000;

    private final AssessmentRepository assessments;
    private final LearningAnswerRepository answers;
    private final EvaluatorAgent evaluator;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final StepProgressService stepProgress;

    public GradingService(AssessmentRepository assessments, LearningAnswerRepository answers,
                          EvaluatorAgent evaluator, ObjectMapper mapper, TransactionTemplate tx,
                          StepProgressService stepProgress) {
        this.assessments = assessments;
        this.answers = answers;
        this.evaluator = evaluator;
        this.mapper = mapper;
        this.tx = tx;
        this.stepProgress = stepProgress;
    }

    public GradingResponse grade(Long assessmentId, AnswersRequest request) {
        Assessment assessment = assessments.findWithGoalById(assessmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "진단을 찾을 수 없습니다: " + assessmentId));
        if (assessment.getResult() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 채점된 진단입니다.");
        }

        DiagnosticQuiz quiz = read(assessment.getQuestions(), DiagnosticQuiz.class);
        Map<Integer, String> submitted = collectAnswers(request, quiz);

        GradingResult result = LlmCalls.run(() -> evaluator.grade(
                assessment.getGoal().getSubject().getName(), assessment.getGoal().getGoalText(),
                quiz.questions(), submitted));

        Map<Integer, QuestionResult> byQuestion = result.questionResults().stream()
                .collect(Collectors.toMap(QuestionResult::questionId, r -> r));
        String resultJson = write(result);

        StepOutcome outcome = tx.execute(status -> {
            Assessment managed = assessments.findById(assessmentId).orElseThrow();
            if (managed.getResult() != null) { // 동시에 두 번 제출된 경우
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 채점된 진단입니다.");
            }
            managed.setResult(resultJson);
            if (managed.getType() == AssessmentType.DIAGNOSTIC) { // Step 확인 문제는 쉬워서 현재 수준 판정에 쓰지 않는다
                managed.getGoal().setCurrentLevel(result.level());
            }
            submitted.forEach((questionId, text) -> {
                LearningAnswer answer = new LearningAnswer(managed, questionId, text);
                answer.setScores(write(byQuestion.get(questionId)));
                answers.save(answer);
            });
            return managed.getType() == AssessmentType.STEP
                    ? stepProgress.applyAssessment(managed.getStep(), result.correctness()) : null;
        });
        return new GradingResponse(assessmentId, result, outcome);
    }

    /** 문제 번호 검증, 빈 답변 제외, 길이 제한. 채점할 답변이 하나도 없으면 400. */
    private Map<Integer, String> collectAnswers(AnswersRequest request, DiagnosticQuiz quiz) {
        if (request == null || request.answers() == null) {
            throw badRequest("answers는 필수입니다.");
        }
        Set<Integer> validIds = quiz.questions().stream().map(DiagnosticQuestion::id).collect(Collectors.toSet());
        Map<Integer, String> collected = new LinkedHashMap<>();
        for (AnswersRequest.Item item : request.answers()) {
            if (item == null || item.questionId() == null || !validIds.contains(item.questionId())) {
                throw badRequest("존재하지 않는 questionId입니다: " + (item == null ? null : item.questionId()));
            }
            if (item.answer() != null && item.answer().length() > MAX_ANSWER_CHARS) {
                throw badRequest("답변은 " + MAX_ANSWER_CHARS + "자 이하여야 합니다. (questionId " + item.questionId() + ")");
            }
            if (item.answer() == null || item.answer().isBlank()) {
                continue;
            }
            if (collected.put(item.questionId(), item.answer().trim()) != null) {
                throw badRequest("questionId가 중복되었습니다: " + item.questionId());
            }
        }
        if (collected.isEmpty()) {
            throw badRequest("채점할 답변이 없습니다.");
        }
        return collected;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 JSON을 읽을 수 없습니다.", e);
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
