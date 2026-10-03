package com.personal.ai.api.calendar;

import com.personal.ai.api.learning.CurriculumResponse;
import com.personal.ai.api.learning.DashboardService;
import com.personal.ai.api.learning.GoalDetail;
import com.personal.ai.core.learning.StepStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 캘린더 구독(ICS)용 일정을 만든다. LLM을 호출하지 않고 이미 있는 커리큘럼에서 계산한다.
 * - 목표 마감일(종일 일정)
 * - 학습 계획: 남은 Step을 하루에 하나씩, 매일 같은 시각에 배치한다(보충 필요 Step이 먼저, 여러 목표는 번갈아).
 *   이 계획은 "제안"이다. Step을 끝내면 다음 구독 갱신 때 일정이 앞으로 당겨진다.
 */
@Service
public class CalendarService {

    static final int MIN_MINUTES = 30;
    static final int MAX_MINUTES = 120;
    static final int DEFAULT_MINUTES = 60;

    private final DashboardService dashboard;
    private final Clock clock;
    private final ZoneId zone;
    private final LocalTime start;
    private final int days;

    public CalendarService(DashboardService dashboard, Clock clock,
                           @Value("${calendar.zone:Asia/Seoul}") String zone,
                           @Value("${calendar.start:21:00}") String start,
                           @Value("${calendar.days:14}") int days) {
        this.dashboard = dashboard;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
        this.start = LocalTime.parse(start);
        this.days = Math.max(1, Math.min(60, days));
    }

    @Transactional(readOnly = true)
    public String ics() {
        List<GoalDetail> goals = dashboard.goals().stream().map(g -> dashboard.goal(g.goalId())).toList();
        List<IcsWriter.Event> events = new ArrayList<>();

        for (GoalDetail g : goals) {
            var s = g.goal();
            if (s.deadline() != null && s.progressPercent() < 100) {
                events.add(IcsWriter.Event.allDay("deadline-" + s.goalId() + "@personal-ai",
                        "마감: " + s.subject(), s.goalText() + " (진행률 " + s.progressPercent() + "%)", s.deadline()));
            }
        }

        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Planned> plan = plan(goals);
        for (int i = 0; i < Math.min(days, plan.size()); i++) {
            Planned p = plan.get(i);
            Instant from = today.plusDays(i).atTime(start).atZone(zone).toInstant();
            events.add(IcsWriter.Event.timed("step-" + p.step.id() + "@personal-ai", p.title(), p.step.objective(),
                    from, from.plus(Duration.ofMinutes(minutes(p.step)))));
        }
        return IcsWriter.calendar("Personal AI 학습", events, Instant.now(clock));
    }

    record Planned(String subject, CurriculumResponse.Step step) {
        String title() {
            String what = switch (step.status()) {
                case REVIEW_REQUIRED -> "복습";
                case ASSESSMENT -> "확인 문제";
                default -> "학습";
            };
            return "[" + subject + "] Step " + step.seq() + " " + step.title() + " " + what;
        }
    }

    /** 목표마다 남은 Step(보충 필요가 먼저, 그다음 번호순)을 줄 세우고, 목표들을 번갈아 한 줄로 합친다. */
    static List<Planned> plan(List<GoalDetail> goals) {
        List<List<Planned>> queues = new ArrayList<>();
        for (GoalDetail g : goals) {
            List<Planned> queue = g.steps().stream()
                    .filter(s -> s.status() != StepStatus.COMPLETED)
                    .sorted(Comparator.comparing((CurriculumResponse.Step s) -> s.status() == StepStatus.REVIEW_REQUIRED ? 0 : 1)
                            .thenComparingInt(CurriculumResponse.Step::seq))
                    .map(s -> new Planned(g.goal().subject(), s)).toList();
            if (!queue.isEmpty()) queues.add(queue);
        }
        List<Planned> merged = new ArrayList<>();
        for (int round = 0; ; round++) {
            boolean any = false;
            for (List<Planned> q : queues) {
                if (round < q.size()) {
                    merged.add(q.get(round));
                    any = true;
                }
            }
            if (!any) return merged;
        }
    }

    private static int minutes(CurriculumResponse.Step step) {
        int m = step.estimatedMinutes() > 0 ? step.estimatedMinutes() : DEFAULT_MINUTES;
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, m));
    }
}
