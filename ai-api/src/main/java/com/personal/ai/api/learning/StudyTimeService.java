package com.personal.ai.api.learning;

import com.personal.ai.core.learning.StepStatus;
import com.personal.ai.data.learning.LearningSession;
import com.personal.ai.data.learning.LearningSessionRepository;
import com.personal.ai.data.learning.LearningStep;
import com.personal.ai.data.learning.LearningStepRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 학습 시간 기록. 화면이 "열려 있고 탭이 보이는 동안"의 초를 주기적으로 보내면 Step·날짜별로 더한다.
 * 클라이언트가 보내는 값이라 한 번에 보낼 수 있는 양과 하루 상한을 둔다(실수나 조작으로 통계가 망가지지 않게).
 */
@Service
public class StudyTimeService {

    public static final int MAX_SECONDS_PER_CALL = 120;
    public static final int MAX_SECONDS_PER_STEP_PER_DAY = 8 * 3600;

    private final LearningStepRepository steps;
    private final LearningSessionRepository sessions;
    private final TransactionTemplate tx;
    private final Clock clock;

    public StudyTimeService(LearningStepRepository steps, LearningSessionRepository sessions,
                            TransactionTemplate tx, Clock clock) {
        this.steps = steps;
        this.sessions = sessions;
        this.tx = tx;
        this.clock = clock;
    }

    /** @return 오늘 이 Step의 누적 초 */
    public int add(Long stepId, int seconds) {
        if (seconds < 1 || seconds > MAX_SECONDS_PER_CALL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seconds는 1~" + MAX_SECONDS_PER_CALL + "이어야 합니다.");
        }
        LearningStep step = steps.findById(stepId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step을 찾을 수 없습니다: " + stepId));
        if (step.getStatus() == StepStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "아직 열리지 않은 Step입니다.");
        }
        LocalDate today = LocalDate.now(clock);
        try {
            return addOnce(step, today, seconds);
        } catch (DataIntegrityViolationException raced) { // 같은 날 첫 기록이 동시에 들어온 경우: 이미 생긴 행에 더한다
            return addOnce(step, today, seconds);
        }
    }

    private int addOnce(LearningStep step, LocalDate today, int seconds) {
        return tx.execute(s -> {
            LearningSession row = sessions.findByStepIdAndStudyDate(step.getId(), today)
                    .orElseGet(() -> sessions.saveAndFlush(new LearningSession(step, today, 0)));
            row.setSeconds(Math.min(MAX_SECONDS_PER_STEP_PER_DAY, row.getSeconds() + seconds));
            return sessions.save(row).getSeconds();
        });
    }
}
