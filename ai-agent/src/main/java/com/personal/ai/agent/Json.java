package com.personal.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** 응답 검증용 공통 헬퍼. 위반하면 AgentResponseException. */
public final class Json {

    private Json() {}

    public static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    /** min~max 범위의 정수 필드. 문자열 숫자("80")나 실수(8.5)는 거부한다. */
    public static int intInRange(JsonNode node, String field, int min, int max, String label) {
        JsonNode value = node.path(field);
        if (!value.isInt() || value.asInt() < min || value.asInt() > max) {
            throw new AgentResponseException(label + ": " + field + "는 " + min + "~" + max + " 정수여야 합니다.");
        }
        return value.asInt();
    }

    /** 문자열 배열. 빈 값은 버리고 최대 limit개만 취한다. */
    public static List<String> strings(JsonNode node, String field, int limit) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : node.path(field)) {
            String text = item.asText("").trim();
            if (!text.isEmpty() && values.size() < limit) {
                values.add(text);
            }
        }
        return values;
    }
}
