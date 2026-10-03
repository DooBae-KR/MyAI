package com.personal.ai.api.notify;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.personal.ai.api.learning.CurriculumResponse;
import com.personal.ai.api.learning.GoalDetail;
import com.personal.ai.api.learning.GoalSummary;
import com.personal.ai.api.stats.StatsResponse;
import com.personal.ai.core.learning.StepStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderMessageTest {

    private static GoalDetail goal(String next, List<CurriculumResponse.Step> steps) {
        return new GoalDetail(new GoalSummary(1L, "Vue", "실무", null, null, null, 4, 1, 25), next, null, null, null, steps);
    }

    private static CurriculumResponse.Step step(int seq, StepStatus st) {
        return new CurriculumResponse.Step((long) seq, seq, "T" + seq, "o", 2, 30, List.of(), st);
    }

    @Test
    void pointsToFirstOpenStepSkippingCompletedAndLocked() {
        String m = ReminderMessage.build(List.of(goal("LEARNING",
                List.of(step(1, StepStatus.COMPLETED), step(2, StepStatus.LEARNING), step(3, StepStatus.LOCKED)))),
                new StatsResponse.Day("2026-10-03", 2, 1));
        assertTrue(m.contains("**Vue** 1/4 (25%) → Step 2 T2 (약 30분)"), m);
        assertTrue(m.contains("진단 답변 2개, 코딩 풀이 1개"), m);
    }

    @Test
    void earlyStagesAndEmptyAndAllDone() {
        var day = new StatsResponse.Day("d", 0, 0);
        assertTrue(ReminderMessage.build(List.of(goal("DIAGNOSTIC_NEEDED", List.of())), day).contains("진단 문제 만들기"));
        assertTrue(ReminderMessage.build(List.of(), day).contains("등록된 학습 목표가 없습니다"));
        assertTrue(ReminderMessage.build(List.of(goal("LEARNING", List.of(step(1, StepStatus.COMPLETED)))), day).contains("모든 단계 완료"));
    }
}
