package com.personal.ai.agent;

import com.personal.ai.core.model.AiModel;
import com.personal.ai.core.model.AiRequest;
import com.personal.ai.core.model.AiResponse;

import java.util.ArrayList;
import java.util.List;

/** 준비된 응답을 순서대로 돌려주고(마지막 응답 반복), 받은 요청을 기록한다. */
public class FakeModel implements AiModel {
    private final List<String> responses;
    public final List<AiRequest> requests = new ArrayList<>();

    public FakeModel(String... responses) {
        this.responses = List.of(responses);
    }

    @Override
    public AiResponse chat(AiRequest request) {
        requests.add(request);
        return new AiResponse(responses.get(Math.min(requests.size(), responses.size()) - 1));
    }
}
