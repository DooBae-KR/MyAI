package com.personal.ai.api.stats;

import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.codingtest.CodingProblemRepository;
import com.personal.ai.data.codingtest.CodingSubmissionRepository;
import com.personal.ai.data.learning.LearningAnswerRepository;
import com.personal.ai.data.learning.LearningGoalRepository;
import com.personal.ai.data.learning.LearningStepRepository;
import com.personal.ai.data.learning.ThinkingPatternRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 학습 통계 조회. LLM을 호출하지 않는다. */
@Service
public class StatsService {

    static final int DAYS = 14;

    private final LearningGoalRepository goals;
    private final LearningStepRepository steps;
    private final LearningAnswerRepository answers;
    private final CodingSubmissionRepository submissions;
    private final CodingProblemRepository problems;
    private final ThinkingPatternRepository patterns;
    private final Clock clock;

    public StatsService(LearningGoalRepository goals, LearningStepRepository steps, LearningAnswerRepository answers,
                        CodingSubmissionRepository submissions, CodingProblemRepository problems,
                        ThinkingPatternRepository patterns, Clock clock) {
        this.goals = goals;
        this.steps = steps;
        this.answers = answers;
        this.submissions = submissions;
        this.problems = problems;
        this.patterns = patterns;
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
                answers.count(), submissions.count(), problems.count());

        LocalDate today = LocalDate.now(clock);
        LocalDateTime since = today.minusDays(DAYS - 1).atStartOfDay();
        List<LocalDateTime> answered = answers.createdSince(since);
        List<LocalDateTime> submitted = submissions.createdSince(since);
        List<StatsResponse.Day> activity = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            activity.add(new StatsResponse.Day(day.toString(),
                    (int) answered.stream().filter(t -> t.toLocalDate().equals(day)).count(),
                    (int) submitted.stream().filter(t -> t.toLocalDate().equals(day)).count()));
        }

        return new StatsResponse(totals, activity,
                submissions.countByCategory().stream()
                        .map(r -> new StatsResponse.Count(r[0] == null ? "분류 없음" : r[0].toString(), ((Number) r[1]).longValue()))
                        .toList(),
                patterns.countByStatus().stream()
                        .map(r -> new StatsResponse.Count(r[0].toString(), ((Number) r[1]).longValue()))
                        .toList());
    }
}
