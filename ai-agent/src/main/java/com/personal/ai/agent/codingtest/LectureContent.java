package com.personal.ai.agent.codingtest;

import java.util.List;

/** 특강. 단계 번호와 제목은 코드가 고정하고, 모델은 본문만 채운다. */
public record LectureContent(String promptVersion, List<String> assumptions, List<Section> sections) {

    public record Section(int no, String title, String body) {}
}
