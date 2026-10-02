package com.personal.ai.data.learning;

import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.Level;
import com.personal.ai.core.learning.StepStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 PostgreSQL(Testcontainers)에서 Flyway 마이그레이션과 JPA 매핑을 함께 검증한다. Docker가 없으면 건너뛴다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LearningRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestEntityManager em;
    @Autowired LearningSubjectRepository subjects;
    @Autowired LearningGoalRepository goals;
    @Autowired LearningStepRepository steps;
    @Autowired AssessmentRepository assessments;
    @Autowired LearningAnswerRepository answers;

    @Test
    void savesGoalWithOrderedStepsPrerequisitesAndJson() {
        LearningSubject js = subjects.save(new LearningSubject("JavaScript", null));
        LearningSubject vue = new LearningSubject("Vue", "프론트엔드 프레임워크");
        vue.getPrerequisites().add(js);
        subjects.save(vue);

        LearningGoal goal = goals.save(new LearningGoal(vue, "실무 수준까지", Level.INTERMEDIATE, null));
        steps.save(new LearningStep(goal, 2, "Component", null, 2, StepStatus.LOCKED, null));
        LearningStep first = steps.save(new LearningStep(goal, 1, "Vue 기본", null, 1, StepStatus.AVAILABLE, "{\"estimatedMinutes\":60}"));

        Assessment diagnostic = assessments.save(new Assessment(goal, null, AssessmentType.DIAGNOSTIC,
                "{\"questions\":[{\"q\":\"Promise란?\"}]}"));
        LearningAnswer answer = answers.save(new LearningAnswer(diagnostic, 1, "비동기 결과를 담는 객체"));
        answer.setScores("{\"correctness\":80}");
        diagnostic.setResult("{\"correctness\":80}");
        em.flush();
        em.clear();

        assertEquals("Vue", subjects.findByNameIgnoreCase("vue").orElseThrow().getName());
        assertEquals(1, subjects.findByNameIgnoreCase("VUE").orElseThrow().getPrerequisites().size());

        List<LearningStep> ordered = steps.findByGoalIdOrderBySeq(goal.getId());
        assertEquals(List.of("Vue 기본", "Component"), ordered.stream().map(LearningStep::getTitle).toList());
        assertEquals(StepStatus.AVAILABLE, steps.findById(first.getId()).orElseThrow().getStatus());

        assertTrue(assessments.findById(diagnostic.getId()).orElseThrow().getQuestions().contains("Promise"));
        LearningAnswer saved = answers.findById(answer.getId()).orElseThrow();
        assertEquals("비동기 결과를 담는 객체", saved.getAnswerText());
        assertEquals(1, saved.getQuestionId());
        assertTrue(assessments.findById(diagnostic.getId()).orElseThrow().getResult().contains("80"));
        assertTrue(steps.findById(first.getId()).orElseThrow().getDetail().contains("60"));
        assertTrue(steps.existsByGoalId(goal.getId()));
        assertTrue(saved.getScores().contains("80"));
    }
}
