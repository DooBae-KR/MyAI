package com.personal.ai.api.mcp;

import com.personal.ai.api.service.AiService;
import com.personal.ai.core.model.AiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpControllerTest {

    private MockMvc mvc(String token) {
        AiService service = new AiService(request -> new AiResponse("echo:" + request.getUserPrompt()));
        return MockMvcBuilders.standaloneSetup(new McpController(service, token)).build();
    }

    private static String rpc(String body) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1," + body + "}";
    }

    @Test
    void listsAndCallsTool() throws Exception {
        MockMvc mvc = mvc("");

        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(rpc("\"method\":\"tools/list\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[0].name").value("ask_assistant"));

        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(rpc(
                        "\"method\":\"tools/call\",\"params\":{\"name\":\"ask_assistant\",\"arguments\":{\"userPrompt\":\"hi\"}}")))
                .andExpect(jsonPath("$.result.content[0].text").value("echo:hi"))
                .andExpect(jsonPath("$.result.isError").value(false));
    }

    @Test
    void rejectsMissingTokenAndAcceptsNotifications() throws Exception {
        mvc("secret").perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("\"method\":\"ping\"")))
                .andExpect(status().isUnauthorized());

        mvc("").perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andExpect(status().isAccepted());
    }
}
