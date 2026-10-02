package com.personal.ai.api.learning;

import com.personal.ai.data.learning.LearningGoal;
import com.personal.ai.data.learning.LearningGoalRepository;
import com.personal.ai.data.learning.LearningSubject;
import com.personal.ai.data.learning.LearningSubjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningService {

    private final LearningSubjectRepository subjects;
    private final LearningGoalRepository goals;

    public LearningService(LearningSubjectRepository subjects, LearningGoalRepository goals) {
        this.subjects = subjects;
        this.goals = goals;
    }

    /** 같은 이름의 분야(대소문자 무시)가 있으면 재사용하고, 없으면 새로 만든 뒤 목표를 등록한다. */
    @Transactional
    public SubjectGoalResponse createSubjectGoal(CreateSubjectRequest request) {
        if (isBlank(request.subject()) || isBlank(request.goal())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject와 goal은 필수입니다.");
        }

        String name = request.subject().trim();
        LearningSubject subject = subjects.findByNameIgnoreCase(name)
                .orElseGet(() -> subjects.save(new LearningSubject(name, null)));
        LearningGoal goal = goals.save(new LearningGoal(
                subject, request.goal().trim(), request.targetLevel(), request.deadline()));

        return new SubjectGoalResponse(subject.getId(), subject.getName(), goal.getId(), goal.getGoalText());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
