package com.personal.ai.api.codingtest;

import com.personal.ai.agent.codingtest.LectureContent;
import com.personal.ai.api.codingtest.CodingDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coding/problems")
public class CodingTestController {

    private final CodingTestService service;

    public CodingTestController(CodingTestService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProblemSummary> list() {
        return service.list();
    }

    /** 문제 등록. 원문이 아니라 출처, 제목, 난이도, 태그와 직접 쓴 요약을 받는다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemDetail create(@RequestBody CreateProblem request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public ProblemDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    /** AI 특강(17단계)을 만든다. 다시 호출하면 새로 만들고, 가장 최근 것이 쓰인다. */
    @PostMapping("/{id}/lecture")
    @ResponseStatus(HttpStatus.CREATED)
    public LectureContent lecture(@PathVariable Long id) {
        return service.generateLecture(id);
    }

    /** 내 풀이를 제출해 권장 접근과 비교한다. 코드를 실행하지 않고 읽고 분석한다. */
    @PostMapping("/{id}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionView submit(@PathVariable Long id, @RequestBody SubmissionRequest request) {
        return service.submit(id, request);
    }
}
