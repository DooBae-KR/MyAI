package com.personal.ai.api.learning;

import com.personal.ai.agent.evaluator.DiagnosticQuiz;

import java.util.List;

/** 학습자에게 보여 주는 진단 문제. 채점 기준(keyPoints)은 정답 힌트가 되므로 일부러 뺀다. */
public record QuizView(String promptVersion, List<Question> questions) {

    public record Question(int id, String area, String type, int difficulty, String question) {}

    public static QuizView from(DiagnosticQuiz quiz) {
        return new QuizView(quiz.promptVersion(), quiz.questions().stream()
                .map(q -> new Question(q.id(), q.area(), q.type(), q.difficulty(), q.question())).toList());
    }
}
