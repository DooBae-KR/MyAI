package com.personal.ai.api.codingtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.codingtest.CodingTestAgent;
import com.personal.ai.agent.codingtest.LectureContent;
import com.personal.ai.agent.codingtest.ProblemInfo;
import com.personal.ai.agent.codingtest.ReviewContent;
import com.personal.ai.api.codingtest.CodingDtos.*;
import com.personal.ai.api.learning.LlmCalls;
import com.personal.ai.data.codingtest.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 코딩테스트 문제(메타데이터와 사용자가 쓴 요약), AI 특강, 풀이 비교.
 * LLM 호출이 오래 걸릴 수 있어 @Transactional을 쓰지 않는다. 저장은 호출이 성공한 뒤에만 한다.
 */
@Service
public class CodingTestService {

    static final int MAX_CODE_CHARS = 20_000;
    static final int MAX_EXPLANATION_CHARS = 4_000;

    private final CodingProblemRepository problems;
    private final CodingLectureRepository lectures;
    private final CodingSubmissionRepository submissions;
    private final CodingTestAgent agent;
    private final ObjectMapper mapper;

    public CodingTestService(CodingProblemRepository problems, CodingLectureRepository lectures,
                             CodingSubmissionRepository submissions, CodingTestAgent agent, ObjectMapper mapper) {
        this.problems = problems;
        this.lectures = lectures;
        this.submissions = submissions;
        this.agent = agent;
        this.mapper = mapper;
    }

    public ProblemDetail create(CreateProblem request) {
        String title = clean(request.title(), 200, "title");
        if (title == null) {
            throw badRequest("title은 필수입니다.");
        }
        String url = clean(request.sourceUrl(), 500, "sourceUrl");
        if (url != null && !url.matches("(?i)https?://\\S+")) {
            throw badRequest("sourceUrl은 http:// 또는 https:// 로 시작하는 주소여야 합니다.");
        }
        Integer minutes = request.estimatedTime();
        if (minutes != null && (minutes < 1 || minutes > 1000)) {
            throw badRequest("estimatedTime은 1~1000(분)이어야 합니다.");
        }
        CodingProblem saved = problems.save(new CodingProblem(title).details(
                clean(request.source(), 50, "source"), url, clean(request.externalId(), 100, "externalId"),
                clean(request.company(), 100, "company"), clean(request.difficulty(), 30, "difficulty"),
                clean(request.category(), 100, "category"), clean(request.algorithm(), 200, "algorithm"),
                clean(request.dataStructure(), 200, "dataStructure"), clean(request.language(), 30, "language"),
                clean(request.summary(), 2000, "summary"), minutes));
        return detail(saved);
    }

    public List<ProblemSummary> list() {
        return problems.findAllByOrderByIdDesc().stream().map(p -> new ProblemSummary(p.getId(), p.getTitle(), p.getSource(),
                p.getDifficulty(), p.getCategory(), p.getAlgorithm(), p.getDataStructure(), p.getLanguage(),
                p.getEstimatedTime(), lectures.findFirstByProblemIdOrderByIdDesc(p.getId()).isPresent(),
                submissions.countByProblemId(p.getId()))).toList();
    }

    public ProblemDetail get(Long id) {
        return detail(find(id));
    }

    /** 특강을 (다시) 만든다. 가장 최근 것이 쓰인다. */
    public LectureContent generateLecture(Long id) {
        CodingProblem problem = find(id);
        LectureContent lecture = LlmCalls.run(() -> agent.lecture(info(problem)));
        lectures.save(new CodingLecture(problem, lecture.promptVersion(), write(lecture)));
        return lecture;
    }

    public SubmissionView submit(Long id, SubmissionRequest request) {
        CodingProblem problem = find(id);
        String language = clean(request.language(), 30, "language");
        if (language == null) {
            throw badRequest("language는 필수입니다.");
        }
        if (request.code() == null || request.code().isBlank()) {
            throw badRequest("code는 필수입니다.");
        }
        if (request.code().length() > MAX_CODE_CHARS) {
            throw badRequest("code는 " + MAX_CODE_CHARS + "자 이하여야 합니다.");
        }
        String explanation = clean(request.explanation(), MAX_EXPLANATION_CHARS, "explanation");

        ReviewContent review = LlmCalls.run(() -> agent.review(info(problem), language, request.code(), explanation));
        CodingSubmission saved = submissions.save(new CodingSubmission(problem, language, request.code(), explanation, write(review)));
        return new SubmissionView(saved.getId(), language, request.code(), explanation, review, saved.getCreatedAt());
    }

    private ProblemDetail detail(CodingProblem p) {
        LectureContent lecture = lectures.findFirstByProblemIdOrderByIdDesc(p.getId())
                .map(l -> read(l.getContent(), LectureContent.class)).orElse(null);
        List<SubmissionView> history = submissions.findByProblemIdOrderByIdDesc(p.getId()).stream()
                .map(s -> new SubmissionView(s.getId(), s.getLanguage(), s.getCode(), s.getExplanation(),
                        s.getReview() == null ? null : read(s.getReview(), ReviewContent.class), s.getCreatedAt())).toList();
        return new ProblemDetail(p.getId(), p.getTitle(), p.getSource(), p.getSourceUrl(), p.getExternalId(), p.getCompany(),
                p.getDifficulty(), p.getCategory(), p.getAlgorithm(), p.getDataStructure(), p.getLanguage(),
                p.getDescriptionSummary(), p.getEstimatedTime(), lecture, history);
    }

    private CodingProblem find(Long id) {
        return problems.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문제를 찾을 수 없습니다: " + id));
    }

    private static ProblemInfo info(CodingProblem p) {
        return new ProblemInfo(p.getTitle(), p.getSource(), p.getSourceUrl(), p.getCompany(), p.getDifficulty(),
                p.getCategory(), p.getAlgorithm(), p.getDataStructure(), p.getLanguage(), p.getDescriptionSummary(),
                p.getEstimatedTime());
    }

    /** 앞뒤 공백을 지우고 비면 null. 길이를 넘으면 400. */
    private static String clean(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.length() > max) {
            throw badRequest(field + "은 " + max + "자 이하여야 합니다.");
        }
        return v;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 JSON을 읽을 수 없습니다.", e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
