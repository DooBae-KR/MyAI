package com.personal.ai.api.learning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService learningService;
    private final DiagnosticService diagnosticService;

    public LearningController(LearningService learningService, DiagnosticService diagnosticService) {
        this.learningService = learningService;
        this.diagnosticService = diagnosticService;
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
}
