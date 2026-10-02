package com.personal.ai.agent.curriculum;

import java.util.List;

public record PlannedStep(int seq, String title, String objective, int difficulty,
                          int estimatedMinutes, List<String> practiceTasks) {
}
