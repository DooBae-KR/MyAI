package com.personal.ai.data.codingtest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodingSubmissionRepository extends JpaRepository<CodingSubmission, Long> {
    List<CodingSubmission> findByProblemIdOrderByIdDesc(Long problemId);

    int countByProblemId(Long problemId);
}
