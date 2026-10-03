package com.personal.ai.agent.pattern;

/** improvementStrategy는 "개선되었다"를 판단할 기준이다(없을 수 있음). */
public record KnownPattern(String name, String description, String improvementStrategy) {
    public KnownPattern(String name, String description) {
        this(name, description, null);
    }
}
