package com.personal.ai.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    @RestController
    static class Thrower {
        @GetMapping("/expected")
        String expected() { throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 채점된 진단입니다."); }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Thrower())
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void expectedErrorsKeepStatusAndExposeOnlyTheirMessage() throws Exception {
        mvc.perform(get("/expected"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 채점된 진단입니다."));
    }
}
