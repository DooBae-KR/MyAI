package com.personal.ai.agent.tutor;

import java.util.List;

/** Step 학습 자료. 모델이 쓴 본문만 담고, 검증을 통과한 것만 만들어진다. */
public record LessonContent(String promptVersion, String overview, List<Concept> concepts, List<Example> examples,
                            String implementationTask, List<String> commonMistakes, List<String> checklist) {

    public record Concept(String title, String explanation) {}

    public record Example(String title, String language, String code, String explanation) {}
}
