package com.personal.ai.api.learning;

import com.personal.ai.core.learning.Level;

import java.time.LocalDate;

/** targetLevel, deadline은 선택. */
public record CreateSubjectRequest(String subject, String goal, Level targetLevel, LocalDate deadline) {
}
