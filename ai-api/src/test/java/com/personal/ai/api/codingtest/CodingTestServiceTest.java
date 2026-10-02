package com.personal.ai.api.codingtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.codingtest.*;
import com.personal.ai.api.codingtest.CodingDtos.*;
import com.personal.ai.data.codingtest.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CodingTestServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CodingProblemRepository problems = mock(CodingProblemRepository.class);
    private final CodingLectureRepository lectures = mock(CodingLectureRepository.class);
    private final CodingSubmissionRepository submissions = mock(CodingSubmissionRepository.class);
    private final CodingTestAgent agent = mock(CodingTestAgent.class);
    private final CodingTestService service = new CodingTestService(problems, lectures, submissions, agent, mapper);

    private CodingProblem problem() {
        CodingProblem p = new CodingProblem("미로 탐색").details("프로그래머스", "https://example.com/p/1", null, null,
                "Lv.2", "그래프", "BFS", "Queue", "Java", "최소 이동 횟수를 구한다.", 40);
        ReflectionTestUtils.setField(p, "id", 1L);
        return p;
    }

    private static CreateProblem create(String title, String url, Integer minutes) {
        return new CreateProblem(title, " 프로그래머스 ", url, null, null, "Lv.2", null, null, null, "Java", " 요약 ", minutes);
    }

    private HttpStatus statusOf(Runnable call) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
    }

    private static LectureContent lecture() {
        List<LectureContent.Section> sections = new java.util.ArrayList<>();
        for (int i = 0; i < CodingTestAgent.LECTURE_TITLES.size(); i++) {
            sections.add(new LectureContent.Section(i + 1, CodingTestAgent.LECTURE_TITLES.get(i), "본문 " + i));
        }
        return new LectureContent("1", List.of("N은 10만이라고 가정"), sections);
    }

    private static ReviewContent review() {
        List<ReviewContent.Comparison> comps = CodingTestAgent.COMPARISON_ITEMS.stream()
                .map(i -> new ReviewContent.Comparison(i, "DFS", "BFS", "DIFFERENT", "최단거리이므로")).toList();
        return new ReviewContent("1", "DFS", "BFS", comps, List.of("방문 체크"), List.of("BFS 3문제"), List.of(), "차이가 있다");
    }

    @Test
    void createsProblemWithTrimmedFieldsAndNeverStoresOriginalStatement() {
        when(problems.save(any())).thenAnswer(i -> i.getArgument(0));

        ProblemDetail created = service.create(create("  미로 탐색 ", "https://example.com/p/1", 40));

        assertEquals("미로 탐색", created.title());
        assertEquals("프로그래머스", created.source());
        assertEquals("요약", created.summary());
        assertNull(created.lecture());
        assertTrue(created.submissions().isEmpty());
    }

    @Test
    void rejectsInvalidProblemInput() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(create(" ", null, null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(create("t", "javascript:alert(1)", null)))); // 링크로 렌더링되므로 http(s)만
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(create("t", "ftp://example.com", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(create("t", null, 0))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(create("t", null, 5000))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(create("가".repeat(201), null, null))));
        verify(problems, never()).save(any());
    }

    @Test
    void generatesAndStoresLectureOnlyAfterTheModelSucceeds() {
        when(problems.findById(1L)).thenReturn(Optional.of(problem()));
        when(agent.lecture(any())).thenReturn(lecture());

        LectureContent result = service.generateLecture(1L);

        assertEquals(17, result.sections().size());
        ArgumentCaptor<ProblemInfo> sent = ArgumentCaptor.forClass(ProblemInfo.class);
        verify(agent).lecture(sent.capture());
        assertEquals("최소 이동 횟수를 구한다.", sent.getValue().summary()); // 원문이 아니라 사용자의 요약
        ArgumentCaptor<CodingLecture> saved = ArgumentCaptor.forClass(CodingLecture.class);
        verify(lectures).save(saved.capture());
        assertTrue(saved.getValue().getContent().contains("문제 이해"));

        when(agent.lecture(any())).thenThrow(new AgentResponseException("17개가 아닙니다"));
        assertEquals(HttpStatus.BAD_GATEWAY, statusOf(() -> service.generateLecture(1L)));
        verify(lectures, times(1)).save(any()); // 실패한 호출은 저장하지 않는다
    }

    @Test
    void submitsSolutionAndStoresReview() {
        when(problems.findById(1L)).thenReturn(Optional.of(problem()));
        when(agent.review(any(), eq("Java"), eq("int main(){}"), eq("DFS로 풀었다"))).thenReturn(review());
        when(submissions.save(any())).thenAnswer(i -> i.getArgument(0));

        SubmissionView view = service.submit(1L, new SubmissionRequest(" Java ", "int main(){}", " DFS로 풀었다 "));

        assertEquals("Java", view.language());
        assertEquals(8, view.review().comparisons().size());
        ArgumentCaptor<CodingSubmission> saved = ArgumentCaptor.forClass(CodingSubmission.class);
        verify(submissions).save(saved.capture());
        assertEquals("int main(){}", saved.getValue().getCode()); // 코드는 그대로 보관한다
        assertTrue(saved.getValue().getReview().contains("알고리즘 선택"));
    }

    @Test
    void rejectsBadSubmissionsAndMissingProblemWithoutCallingTheModel() {
        when(problems.findById(1L)).thenReturn(Optional.of(problem()));
        when(problems.findById(404L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.submit(404L, new SubmissionRequest("Java", "code", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.submit(1L, new SubmissionRequest(" ", "code", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.submit(1L, new SubmissionRequest("Java", "  ", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.submit(1L,
                new SubmissionRequest("Java", "x".repeat(CodingTestService.MAX_CODE_CHARS + 1), null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.submit(1L,
                new SubmissionRequest("Java", "code", "가".repeat(CodingTestService.MAX_EXPLANATION_CHARS + 1)))));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.generateLecture(404L)));

        verifyNoInteractions(agent);
        verify(submissions, never()).save(any());
    }

    @Test
    void listAndDetailShowLectureAndSubmissionHistory() throws Exception {
        CodingProblem p = problem();
        when(problems.findAllByOrderByIdDesc()).thenReturn(List.of(p));
        when(problems.findById(1L)).thenReturn(Optional.of(p));
        when(lectures.findFirstByProblemIdOrderByIdDesc(1L)).thenReturn(
                Optional.of(new CodingLecture(p, "1", mapper.writeValueAsString(lecture()))));
        when(submissions.countByProblemId(1L)).thenReturn(1);
        when(submissions.findByProblemIdOrderByIdDesc(1L)).thenReturn(List.of(
                new CodingSubmission(p, "Java", "code", "설명", mapper.writeValueAsString(review()))));

        ProblemSummary summary = service.list().get(0);
        assertTrue(summary.hasLecture());
        assertEquals(1, summary.submissionCount());

        ProblemDetail detail = service.get(1L);
        assertEquals("문제 이해", detail.lecture().sections().get(0).title());
        assertEquals("DFS", detail.submissions().get(0).review().userApproach());
    }
}
