package com.personal.ai.api.stats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.evaluator.GradingResult;
import com.personal.ai.core.learning.AssessmentType;
import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.Assessment;
import com.personal.ai.data.learning.AssessmentRepository;
import com.personal.ai.data.codingtest.CodingProblemRepository;
import com.personal.ai.data.codingtest.CodingSubmissionRepository;
import com.personal.ai.data.learning.LearningAnswerRepository;
import com.personal.ai.data.learning.LearningGoalRepository;
import com.personal.ai.data.learning.LearningSessionRepository;
import com.personal.ai.data.learning.LearningStepRepository;
import com.personal.ai.data.learning.ThinkingPatternRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 학습 통계 조회. LLM을 호출하지 않는다. */
@Service
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);
    static final int DAYS = 14;
    static final int WEAK_AREAS = 5;

    private final LearningGoalRepository goals;
    private final LearningStepRepository steps;
    private final LearningAnswerRepository answers;
    private final CodingSubmissionRepository submissions;
    private final CodingProblemRepository problems;
    private final ThinkingPatternRepository patterns;
    private final LearningSessionRepository sessions;
    private final AssessmentRepository assessments;
    private final ObjectMapper mapper;
    private final Clock clock;

    public StatsService(LearningGoalRepository goals, LearningStepRepository steps, LearningAnswerRepository answers,
                        CodingSubmissionRepository submissions, CodingProblemRepository problems,
                        ThinkingPatternRepository patterns, LearningSessionRepository sessions, AssessmentRepository assessments,
                        ObjectMapper mapper, Clock clock) {
        this.goals = goals;
        this.steps = steps;
        this.answers = answers;
        this.submissions = submissions;
        this.problems = problems;
        this.patterns = patterns;
        this.sessions = sessions;
        this.assessments = assessments;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        long total = 0, completed = 0;
        for (Object[] row : steps.countByGoalAndStatus()) {
            long n = ((Number) row[2]).longValue();
            total += n;
            if (row[1] == StepStatus.COMPLETED) completed += n;
        }
        var totals = new StatsResponse.Totals(goals.count(), total, completed,
                answers.count(), submissions.count(), problems.count(), Math.round(sessions.totalSeconds() / 60.0));

        LocalDate today = LocalDate.now(clock);
        LocalDateTime since = today.minusDays(DAYS - 1).atStartOfDay();
        List<LocalDateTime> answered = answers.createdSince(since);
        List<LocalDateTime> submitted = submissions.createdSince(since);
        java.util.Map<LocalDate, Long> studied = new java.util.HashMap<>();
        for (Object[] row : sessions.secondsByDaySince(today.minusDays(DAYS - 1))) {
            studied.put((LocalDate) row[0], ((Number) row[1]).longValue());
        }
        List<StatsResponse.Day> activity = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            activity.add(new StatsResponse.Day(day.toString(),
                    (int) answered.stream().filter(t -> t.toLocalDate().equals(day)).count(),
                    (int) submitted.stream().filter(t -> t.toLocalDate().equals(day)).count(),
                    (int) Math.round(studied.getOrDefault(day, 0L) / 60.0)));
        }

        return new StatsResponse(totals, activity,
                submissions.countByCategory().stream()
                        .map(r -> new StatsResponse.Count(r[0] == null ? "분류 없음" : r[0].toString(), ((Number) r[1]).longValue()))
                        .toList(),
                patterns.countByStatus().stream()
                        .map(r -> new StatsResponse.Count(r[0].toString(), ((Number) r[1]).longValue()))
                        .toList(),
                trends(), weakAreas(), reviews(today));
    }

    /** 보충 필요 Step(점수가 낮은 순). 오늘의 학습에서도 쓴다. */
    @Transactional(readOnly = true)
    public List<StatsResponse.Review> reviews() {
        return reviews(LocalDate.now(clock));
    }

    /** 보충 필요 Step을 점수가 낮은 순으로. 점수를 모르면(채점 기록 없음) 뒤로 보낸다. */
    private List<StatsResponse.Review> reviews(LocalDate today) {
        return steps.findByStatusOrderByGoalIdAscSeqAsc(StepStatus.REVIEW_REQUIRED).stream().map(st -> {
            Integer score = null, daysAgo = null;
            var last = assessments.findFirstByStepIdAndTypeOrderByIdDesc(st.getId(), AssessmentType.STEP)
                    .filter(a -> a.getResult() != null);
            if (last.isPresent()) {
                try {
                    score = mapper.readValue(last.get().getResult(), GradingResult.class).correctness();
                    daysAgo = (int) java.time.temporal.ChronoUnit.DAYS.between(last.get().getCreatedAt().toLocalDate(), today);
                } catch (JsonProcessingException e) {
                    log.warn("통계에서 채점 결과를 읽지 못했습니다: assessment {}", last.get().getId());
                }
            }
            return new StatsResponse.Review(st.getGoal().getId(), st.getGoal().getSubject().getName(), st.getId(),
                    st.getSeq(), st.getTitle(), score, daysAgo);
        }).sorted(Comparator.comparing(StatsResponse.Review::lastScore, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    private record Graded(Assessment assessment, GradingResult result) {}

    private List<Graded> gradedDiagnostics() {
        List<Graded> out = new ArrayList<>();
        for (Assessment a : assessments.findByTypeAndResultIsNotNullOrderByIdAsc(AssessmentType.DIAGNOSTIC)) {
            try {
                out.add(new Graded(a, mapper.readValue(a.getResult(), GradingResult.class)));
            } catch (JsonProcessingException e) {
                log.warn("통계에서 채점 결과를 읽지 못해 건너뜁니다: assessment {}", a.getId());
            }
        }
        return out;
    }

    private List<StatsResponse.Trend> trends() {
        Map<Long, StatsResponse.Trend> byGoal = new LinkedHashMap<>();
        for (Graded g : gradedDiagnostics()) {
            var goal = g.assessment().getGoal();
            byGoal.computeIfAbsent(goal.getId(),
                    id -> new StatsResponse.Trend(id, goal.getSubject().getName(), new ArrayList<>()))
                    .points().add(new StatsResponse.Point(g.assessment().getCreatedAt().toLocalDate().toString(), g.result().correctness()));
        }
        return List.copyOf(byGoal.values());
    }

    private List<StatsResponse.WeakArea> weakAreas() {
        Map<Long, Graded> latest = new LinkedHashMap<>(); // 오래된 순으로 읽으므로 나중 것이 덮어쓴다
        for (Graded g : gradedDiagnostics()) latest.put(g.assessment().getGoal().getId(), g);
        return latest.values().stream()
                .flatMap(g -> g.result().areaScores().stream()
                        .map(a -> new StatsResponse.WeakArea(g.assessment().getGoal().getSubject().getName(), a.area(), a.score())))
                .sorted(Comparator.comparingInt(StatsResponse.WeakArea::score))
                .limit(WEAK_AREAS).toList();
    }
}
