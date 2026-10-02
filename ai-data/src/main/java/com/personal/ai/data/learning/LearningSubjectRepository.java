package com.personal.ai.data.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningSubjectRepository extends JpaRepository<LearningSubject, Long> {
    Optional<LearningSubject> findByNameIgnoreCase(String name);
}
