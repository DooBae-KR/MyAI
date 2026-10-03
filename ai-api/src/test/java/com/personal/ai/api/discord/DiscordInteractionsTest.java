package com.personal.ai.api.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.ai.api.stats.StatsResponse;
import com.personal.ai.api.stats.StatsService;
import com.personal.ai.api.today.TodayResponse;
import com.personal.ai.api.today.TodayService;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DiscordInteractionsTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final String TS = String.valueOf(NOW.getEpochSecond());
    private static final String ALLOWED = "111222333";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TodayService today = mock(TodayService.class);
    private final StatsService stats = mock(StatsService.class);
    private final KeyPair pair = newPair();
    private final String publicKeyHex = HexFormat.of().formatHex(Arrays.copyOfRange(pair.getPublic().getEncoded(), 12, 44));
    private final DiscordCommandService commands = new DiscordCommandService(today, stats, mapper, ALLOWED);

    private static KeyPair newPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String sign(String timestamp, String body) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(pair.getPrivate());
        s.update(timestamp.getBytes());
        s.update(body.getBytes());
        return HexFormat.of().formatHex(s.sign());
    }

    private MockMvc mvc(String publicKey) {
        return MockMvcBuilders.standaloneSetup(new DiscordInteractionController(commands, mapper, clock, publicKey)).build();
    }

    private static String command(String name, String userId) {
        return "{\"type\":2,\"data\":{\"name\":\"" + name + "\"},\"member\":{\"user\":{\"id\":\"" + userId + "\"}}}";
    }

    private org.springframework.test.web.servlet.ResultActions send(MockMvc mvc, String body, String signature, String timestamp) throws Exception {
        var req = post("/discord/interactions").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) req.header("X-Signature-Ed25519", signature);
        if (timestamp != null) req.header("X-Signature-Timestamp", timestamp);
        return mvc.perform(req);
    }

    @Test
    void signatureVerificationAcceptsOnlyTheRightKeyBodyAndFreshTimestamp() throws Exception {
        DiscordSignature verifier = new DiscordSignature(publicKeyHex);
        String body = "{\"type\":1}";
        String sig = sign(TS, body);

        assertTrue(verifier.verify(sig, TS, body, NOW));
        assertFalse(verifier.verify(sig, TS, "{\"type\":2}", NOW));                          // 본문 변조
        assertFalse(verifier.verify(sig, String.valueOf(NOW.getEpochSecond() - 1), body, NOW)); // 타임스탬프 변조
        assertFalse(verifier.verify(sign("1000", body), "1000", body, NOW));                 // 오래된 요청(재전송)
        assertFalse(verifier.verify("zz", TS, body, NOW));                                    // 깨진 16진수
        assertFalse(verifier.verify(null, TS, body, NOW));
        assertFalse(new DiscordSignature(HexFormat.of().formatHex(Arrays.copyOfRange(newPair().getPublic().getEncoded(), 12, 44)))
                .verify(sig, TS, body, NOW));                                                     // 다른 키
    }

    @Test
    void invalidPublicKeyIsRejected() {
        assertThrowsIllegalArgument("abcd");
        assertThrowsIllegalArgument("not-hex");
    }

    private static void assertThrowsIllegalArgument(String key) {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new DiscordSignature(key));
    }

    @Test
    void endpointIsHiddenWithoutAPublicKeyAndRejectsBadSignatures() throws Exception {
        send(mvc(""), "{\"type\":1}", sign(TS, "{\"type\":1}"), TS).andExpect(status().isNotFound());
        send(mvc("not-a-key"), "{\"type\":1}", sign(TS, "{\"type\":1}"), TS).andExpect(status().isNotFound());

        MockMvc mvc = mvc(publicKeyHex);
        send(mvc, "{\"type\":1}", "00".repeat(64), TS).andExpect(status().isUnauthorized());
        send(mvc, "{\"type\":1}", null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void answersPingWithPong() throws Exception {
        String body = "{\"type\":1}";
        send(mvc(publicKeyHex), body, sign(TS, body), TS).andExpect(status().isOk()).andExpect(jsonPath("$.type").value(1));
    }

    @Test
    void allowedUserGetsEphemeralAnswersWithoutMentions() throws Exception {
        when(today.today()).thenReturn(new TodayResponse(List.of(
                new TodayResponse.Todo("PASSED", "Vue · Step 3 합격 (92점)", "#/goals/1", true),
                new TodayResponse.Todo("STEP", "Vue · Step 4 학습 시작", "#/goals/1", false)), null));
        MockMvc mvc = mvc(publicKeyHex);

        String today = command("today", ALLOWED);
        send(mvc, today, sign(TS, today), TS).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(4))
                .andExpect(jsonPath("$.data.flags").value(64))
                .andExpect(jsonPath("$.data.content").value("📚 **오늘의 학습**\n☑ Vue · Step 3 합격 (92점)\n□ Vue · Step 4 학습 시작"))
                .andExpect(jsonPath("$.data.allowed_mentions.parse").isEmpty());

        String next = command("next", ALLOWED);
        send(mvc, next, sign(TS, next), TS).andExpect(jsonPath("$.data.content").value("👉 **다음 할 일**\nVue · Step 4 학습 시작"));
    }

    @Test
    void statsSummarizesNumbers() throws Exception {
        var days = List.of(new StatsResponse.Day("2026-10-03", 2, 1, 45));
        when(stats.stats()).thenReturn(new StatsResponse(new StatsResponse.Totals(1, 6, 3, 9, 1, 2, 90), days, List.of(), List.of(),
                List.of(), List.of(), List.of(new StatsResponse.Review(1L, "Vue", 4L, 4, "T", 55, 0))));

        String text = commands.statsText();

        assertTrue(text.contains("완료한 Step 3/6") && text.contains("학습 시간 총 90분 (오늘 45분)")
                && text.contains("답변 2개, 코딩 풀이 1개") && text.contains("복습할 Step 1개"), text);
    }

    @Test
    void otherUsersAndMissingAllowListGetNoData() throws Exception {
        MockMvc mvc = mvc(publicKeyHex);
        String stranger = command("today", "999");
        send(mvc, stranger, sign(TS, stranger), TS).andExpect(jsonPath("$.data.content").value("허용된 사용자가 아닙니다."));
        org.mockito.Mockito.verifyNoInteractions(today, stats);

        var noAllowList = new DiscordCommandService(today, stats, mapper, "");
        var reply = noAllowList.handle(mapper.readTree(command("stats", ALLOWED)));
        assertTrue(reply.at("/data/content").asText().contains("DISCORD_ALLOWED_USER_ID"));
        org.mockito.Mockito.verifyNoInteractions(today, stats);
    }

    @Test
    void dmInteractionsUseTheTopLevelUserAndUnknownCommandsAreExplained() throws Exception {
        String dm = "{\"type\":2,\"data\":{\"name\":\"nope\"},\"user\":{\"id\":\"" + ALLOWED + "\"}}";

        var reply = commands.handle(mapper.readTree(dm));

        assertTrue(reply.at("/data/content").asText().contains("알 수 없는 명령"));
        assertEquals(64, reply.at("/data/flags").asInt());
    }
}
