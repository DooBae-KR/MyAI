package com.personal.ai.api.learning;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearningControllerTest {

    private final LearningService service = mock(LearningService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new LearningController(service, mock(DiagnosticService.class),
            mock(GradingService.class), mock(CurriculumService.class), mock(DashboardService.class))).build();

    @Test
    void createsSubjectAndReturns201() throws Exception {
        when(service.createSubjectGoal(new CreateSubjectRequest("Vue", "실무 수준까지 배우기", null, null)))
                .thenReturn(new SubjectGoalResponse(1L, "Vue", 10L, "실무 수준까지 배우기"));

        mvc.perform(post("/api/learning/subjects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Vue\",\"goal\":\"실무 수준까지 배우기\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectId").value(1))
                .andExpect(jsonPath("$.goalId").value(10));
    }
}
