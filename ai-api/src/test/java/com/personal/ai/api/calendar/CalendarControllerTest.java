package com.personal.ai.api.calendar;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CalendarControllerTest {

    private static final String TOKEN = "0123456789abcdef-secret";
    private final CalendarService service = mock(CalendarService.class);

    private MockMvc mvc(String configuredToken) {
        return MockMvcBuilders.standaloneSetup(new CalendarController(service, configuredToken)).build();
    }

    @Test
    void servesTheFeedOnlyForTheRightToken() throws Exception {
        when(service.ics()).thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");
        MockMvc mvc = mvc(TOKEN);

        mvc.perform(get("/calendar/" + TOKEN + "/personal-ai.ics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));
        mvc.perform(get("/calendar/wrong-token-value-0000/personal-ai.ics")).andExpect(status().isNotFound());
    }

    @Test
    void feedIsDisabledWhenTokenIsMissingOrTooShort() throws Exception {
        mvc("").perform(get("/calendar//personal-ai.ics")).andExpect(status().isNotFound());
        mvc("short").perform(get("/calendar/short/personal-ai.ics")).andExpect(status().isNotFound());
    }
}
