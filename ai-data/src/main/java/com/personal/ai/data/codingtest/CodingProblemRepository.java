package com.personal.ai.data.codingtest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodingProblemRepository extends JpaRepository<CodingProblem, Long> {
    List<CodingProblem> findAllByOrderByIdDesc();
}
