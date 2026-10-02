package com.personal.ai.llm;

import com.personal.ai.core.model.AiRequest;
import com.personal.ai.llm.claude.ClaudeAiModel;
import com.personal.ai.llm.claude.ClaudeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClaudeAiModelTest {

    @Test
    void sendsMessagesRequestAndJoinsTextBlocks() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://claude.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ClaudeAiModel model = new ClaudeAiModel(builder.build(), new ClaudeProperties());

        server.expect(requestTo("http://claude.test/v1/messages"))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.system").value("sys"))
                .andExpect(jsonPath("$.messages[0].content").value("hi"))
                .andRespond(withSuccess(
                        "{\"content\":[{\"type\":\"text\",\"text\":\"안녕\"},{\"type\":\"text\",\"text\":\"하세요\"}]}",
                        MediaType.APPLICATION_JSON));

        AiRequest request = new AiRequest();
        request.setSystemPrompt("sys");
        request.setUserPrompt("hi");

        assertEquals("안녕하세요", model.chat(request).getContent());
        server.verify();
    }
}
