package com.personal.ai.api.learning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectGoalResponse createSubject(@RequestBody CreateSubjectRequest request) {
        return learningService.createSubjectGoal(request);
    }
}
