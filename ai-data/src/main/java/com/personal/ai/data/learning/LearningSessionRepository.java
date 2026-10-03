package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LearningSessionRepository extends JpaRepository<LearningSession, Long> {

    Optional<LearningSession> findByStepIdAndStudyDate(Long stepId, LocalDate studyDate);

    @Query("select coalesce(sum(s.seconds), 0) from LearningSession s")
    long totalSeconds();

    /** 행 형식: [분야 이름, 합계(초)]. 많이 공부한 분야 순. */
    @Query("select sub.name, sum(s.seconds) from LearningSession s join s.step st join st.goal g join g.subject sub group by sub.name order by sum(s.seconds) desc")
    List<Object[]> secondsBySubject();

    /** 행 형식: [날짜, 그날 합계(초)]. */
    @Query("select s.studyDate, sum(s.seconds) from LearningSession s where s.studyDate >= :since group by s.studyDate")
    List<Object[]> secondsByDaySince(@Param("since") LocalDate since);
}
