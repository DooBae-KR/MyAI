package com.personal.ai.api.learning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService learningService;
    private final DiagnosticService diagnosticService;
    private final GradingService gradingService;
    private final CurriculumService curriculumService;

    public LearningController(LearningService learningService, DiagnosticService diagnosticService,
                              GradingService gradingService, CurriculumService curriculumService) {
        this.learningService = learningService;
        this.diagnosticService = diagnosticService;
        this.gradingService = gradingService;
        this.curriculumService = curriculumService;
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectGoalResponse createSubject(@RequestBody CreateSubjectRequest request) {
        return learningService.createSubjectGoal(request);
    }

    /** 목표의 초기 진단 문제를 LLM으로 생성해 저장한다. 호출할 때마다 새로 생성한다. */
    @PostMapping("/goals/{goalId}/diagnostic")
    @ResponseStatus(HttpStatus.CREATED)
    public DiagnosticResponse createDiagnostic(@PathVariable Long goalId) {
        return diagnosticService.generate(goalId);
    }

    /** 진단 답변을 제출해 채점한다. 현재 수준(currentLevel)이 갱신된다. 진단당 한 번만 가능. */
    @PostMapping("/assessments/{assessmentId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public GradingResponse submitAnswers(@PathVariable Long assessmentId, @RequestBody AnswersRequest request) {
        return gradingService.grade(assessmentId, request);
    }

    /** 채점된 진단 결과로 개인 커리큘럼을 만든다. 목표당 한 번만 가능. */
    @PostMapping("/goals/{goalId}/curriculum")
    @ResponseStatus(HttpStatus.CREATED)
    public CurriculumResponse createCurriculum(@PathVariable Long goalId) {
        return curriculumService.create(goalId);
    }
}
