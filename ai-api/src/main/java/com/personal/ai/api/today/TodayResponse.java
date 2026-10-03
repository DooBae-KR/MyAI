package com.personal.ai.api.today;

import java.util.List;

/** 메인 화면의 "오늘의 학습"(할 일 체크리스트)과 "오늘의 특강". lecture는 후보가 없으면 null. */
public record TodayResponse(List<Todo> todos, Lecture lecture) {

    /** kind: DIAGNOSTIC, ANSWERS, CURRICULUM, STEP, REVIEW, PASSED. done은 오늘 이미 끝낸 일(PASSED). */
    public record Todo(String kind, String label, String link, boolean done) {}

    public record Lecture(Long problemId, String title, String reason) {}
}
