package com.personal.ai.api.notify;

import com.personal.ai.api.learning.CurriculumResponse;
import com.personal.ai.api.learning.GoalDetail;
import com.personal.ai.api.stats.StatsResponse;
import com.personal.ai.core.learning.StepStatus;
import java.util.List;

/** 매일 알림 본문. 발송과 분리해 순수 함수로 둔다. */
final class ReminderMessage {

    private ReminderMessage() {}

    static String build(List<GoalDetail> goals, StatsResponse.Day today, List<StatsResponse.Review> reviews) {
        StringBuilder sb = new StringBuilder("📚 **오늘의 학습**\n");
        if (goals.isEmpty()) {
            sb.append("등록된 학습 목표가 없습니다. 대시보드에서 목표를 추가해 보세요.\n");
        }
        for (GoalDetail g : goals) {
            var s = g.goal();
            sb.append("• **").append(s.subject()).append("** ")
                    .append(s.completedSteps()).append('/').append(s.totalSteps()).append(" (").append(s.progressPercent()).append("%) → ")
                    .append(next(g)).append('\n');
        }
        if (!reviews.isEmpty()) {
            sb.append("\n🔁 **복습 필요**\n");
            reviews.stream().limit(5).forEach(r -> sb.append("• ").append(r.subject()).append(" · Step ").append(r.seq()).append(' ').append(r.title())
                    .append(r.lastScore() == null ? "" : " (마지막 " + r.lastScore() + "점)").append('\n'));
            if (reviews.size() > 5) sb.append("… 외 ").append(reviews.size() - 5).append("개\n");
        }
        sb.append("\n오늘 활동: 진단 답변 ").append(today.answers()).append("개, 코딩 풀이 ").append(today.submissions()).append("개");
        return sb.toString();
    }

    private static String next(GoalDetail g) {
        return switch (g.nextAction()) {
            case "DIAGNOSTIC_NEEDED" -> "진단 문제 만들기";
            case "ANSWERS_NEEDED" -> "진단 답변 제출";
            case "CURRICULUM_NEEDED" -> "커리큘럼 만들기";
            default -> g.steps().stream()
                    .filter(st -> st.status() != StepStatus.COMPLETED && st.status() != StepStatus.LOCKED)
                    .findFirst().map(ReminderMessage::stepLine).orElse("모든 단계 완료 🎉");
        };
    }

    private static String stepLine(CurriculumResponse.Step st) {
        return "Step " + st.seq() + " " + st.title() + (st.estimatedMinutes() > 0 ? " (약 " + st.estimatedMinutes() + "분)" : "");
    }
}
