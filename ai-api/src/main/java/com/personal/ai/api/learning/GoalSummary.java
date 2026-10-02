package com.personal.ai.api.learning;

import com.personal.ai.core.learning.Level;

import java.time.LocalDate;

public record GoalSummary(Long goalId, String subject, String goalText, Level targetLevel, Level currentLevel,
                          LocalDate deadline, int totalSteps, int completedSteps, int progressPercent) {
}
