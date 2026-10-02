package com.personal.ai.api.codingtest;

import com.personal.ai.agent.codingtest.LectureContent;
import com.personal.ai.agent.codingtest.ReviewContent;

import java.time.LocalDateTime;
import java.util.List;

public final class CodingDtos {

    private CodingDtos() {}

    /** summary는 사용자가 직접 쓴 요약이다. 문제 원문을 붙여 넣지 않는다. */
    public record CreateProblem(String title, String source, String sourceUrl, String externalId, String company,
                                String difficulty, String category, String algorithm, String dataStructure,
                                String language, String summary, Integer estimatedTime) {}

    public record ProblemSummary(Long id, String title, String source, String difficulty, String category,
                                 String algorithm, String dataStructure, String language, Integer estimatedTime,
                                 boolean hasLecture, int submissionCount) {}

    public record ProblemDetail(Long id, String title, String source, String sourceUrl, String externalId,
                                String company, String difficulty, String category, String algorithm,
                                String dataStructure, String language, String summary, Integer estimatedTime,
                                LectureContent lecture, List<SubmissionView> submissions) {}

    public record SubmissionRequest(String language, String code, String explanation) {}

    public record SubmissionView(Long id, String language, String code, String explanation, ReviewContent review,
                                 LocalDateTime createdAt) {}
}
