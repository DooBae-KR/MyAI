package com.personal.ai.api.today;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.api.learning.CurriculumResponse;
import com.personal.ai.api.learning.DashboardService;
import com.personal.ai.api.learning.GoalDetail;
import com.personal.ai.api.learning.StepProgressService;
import com.personal.ai.api.stats.StatsResponse;
import com.personal.ai.api.stats.StatsService;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.codingtest.CodingLectureRepository;
import com.personal.ai.data.codingtest.CodingProblem;
import com.personal.ai.data.codingtest.CodingProblemRepository;
import com.personal.ai.data.codingtest.CodingSubmissionRepository;
import com.personal.ai.data.learning.AssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 오늘 할 일과 오늘의 특강을 이미 있는 데이터에서 계산한다. LLM을 호출하지 않는다. */
@Service
public class TodayService {

    private final DashboardService dashboard;
    private final StatsService stats;
    private final AssessmentRepository assessments;
    private final CodingProblemRepository problems;
    private final CodingLectureRepository lectures;
    private final CodingSubmissionRepository submissions;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TodayService(DashboardService dashboard, StatsService stats, AssessmentRepository assessments,
                        CodingProblemRepository problems, CodingLectureRepository lectures,
                        CodingSubmissionRepository submissions, ObjectMapper mapper, Clock clock) {
        this.dashboard = dashboard;
        this.stats = stats;
        this.assessments = assessments;
        this.problems = problems;
        this.lectures = lectures;
        this.submissions = submissions;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TodayResponse today() {
        List<TodayResponse.Todo> todos = new ArrayList<>();

        // 오늘 합격한 Step은 먼저 체크된 채로 보여 준다
        for (var a : assessments.findByTypeAndCreatedAtGreaterThanEqualAndResultIsNotNullOrderByIdAsc(
                AssessmentType.STEP, LocalDate.now(clock).atStartOfDay())) {
            int score = score(a.getResult());
            if (a.getStep() != null && score >= StepProgressService.PASS_SCORE) {
                todos.add(new TodayResponse.Todo("PASSED", a.getGoal().getSubject().getName() + " · Step " + a.getStep().getSeq()
                        + " " + a.getStep().getTitle() + " 합격 (" + score + "점)", goalLink(a.getGoal().getId()), true));
            }
        }

        for (var g : dashboard.goals()) {
            next(dashboard.goal(g.goalId())).ifPresent(todos::add);
        }
        for (StatsResponse.Review r : stats.reviews()) {
            todos.add(new TodayResponse.Todo("REVIEW", r.subject() + " · Step " + r.seq() + " " + r.title() + " 복습"
                    + (r.lastScore() == null ? "" : " (마지막 " + r.lastScore() + "점)"), goalLink(r.goalId()), false));
        }
        return new TodayResponse(todos, lecture().orElse(null));
    }

    /** 목표마다 지금 할 일 하나. 보충 필요 Step은 복습 항목으로 따로 나오므로 여기서는 건너뛴다. */
    static Optional<TodayResponse.Todo> next(GoalDetail g) {
        String subject = g.goal().subject();
        String link = goalLink(g.goal().goalId());
        return switch (g.nextAction()) {
            case "DIAGNOSTIC_NEEDED" -> Optional.of(new TodayResponse.Todo("DIAGNOSTIC", subject + " · 진단 문제 만들기", link, false));
            case "ANSWERS_NEEDED" -> Optional.of(new TodayResponse.Todo("ANSWERS", subject + " · 진단 답변 제출", link, false));
            case "CURRICULUM_NEEDED" -> Optional.of(new TodayResponse.Todo("CURRICULUM", subject + " · 커리큘럼 만들기", link, false));
            default -> g.steps().stream()
                    .filter(s -> s.status() == StepStatus.AVAILABLE || s.status() == StepStatus.LEARNING || s.status() == StepStatus.ASSESSMENT)
                    .findFirst()
                    .map(s -> new TodayResponse.Todo("STEP", subject + " · " + stepLabel(s), link, false));
        };
    }

    private static String stepLabel(CurriculumResponse.Step s) {
        String what = switch (s.status()) {
            case LEARNING -> "학습 이어서";
            case ASSESSMENT -> "확인 문제 풀기";
            default -> "학습 시작";
        };
        return "Step " + s.seq() + " " + s.title() + " " + what;
    }

    /** 특강이 있고 아직 풀이를 내지 않은 문제(오래된 것부터)를 우선, 없으면 가장 최근 특강. */
    private Optional<TodayResponse.Lecture> lecture() {
        List<CodingProblem> oldestFirst = new ArrayList<>(problems.findAllByOrderByIdDesc());
        Collections.reverse(oldestFirst);
        CodingProblem latestWithLecture = null;
        for (CodingProblem p : oldestFirst) {
            if (lectures.findFirstByProblemIdOrderByIdDesc(p.getId()).isEmpty()) continue;
            if (submissions.countByProblemId(p.getId()) == 0) {
                return Optional.of(new TodayResponse.Lecture(p.getId(), p.getTitle(), "아직 풀이를 제출하지 않은 문제입니다"));
            }
            latestWithLecture = p;
        }
        return Optional.ofNullable(latestWithLecture)
                .map(p -> new TodayResponse.Lecture(p.getId(), p.getTitle(), "가장 최근 특강입니다. 다시 복습해 보세요"));
    }

    private static String goalLink(Long goalId) {
        return "#/goals/" + goalId;
    }

    private int score(String resultJson) {
        try {
            return mapper.readValue(resultJson, GradingResult.class).correctness();
        } catch (JsonProcessingException e) {
            return -1;
        }
    }
}
