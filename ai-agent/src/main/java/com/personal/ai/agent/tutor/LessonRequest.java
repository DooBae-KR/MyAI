package com.personal.ai.agent.tutor;

import java.util.List;

/** 학습 자료를 만들 Step과 개인화 참고 자료(약점, 관찰된 학습 습관 가설). */
public record LessonRequest(String subject, String goal, String stepTitle, String objective, int difficulty,
                            List<String> practiceTasks, List<String> learnerWeaknesses, List<Pattern> observedPatterns) {

    public record Pattern(String name, String improvementStrategy) {}
}
