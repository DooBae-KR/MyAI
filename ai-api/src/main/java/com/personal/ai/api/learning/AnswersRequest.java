package com.personal.ai.api.learning;

import java.util.List;

public record AnswersRequest(List<Item> answers) {

    public record Item(Integer questionId, String answer) {}
}
