package com.personal.ai.data.codingtest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CodingLectureRepository extends JpaRepository<CodingLecture, Long> {
    /** 다시 생성할 수 있으므로 가장 최근 특강을 쓴다. */
    Optional<CodingLecture> findFirstByProblemIdOrderByIdDesc(Long problemId);
}
