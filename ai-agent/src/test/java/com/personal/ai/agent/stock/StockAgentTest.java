package com.personal.ai.agent.stock;

import com.personal.ai.agent.AgentResponseException;
import com.personal.ai.agent.FakeModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StockAgentTest {

    private static final StockSnapshot SNAPSHOT = new StockSnapshot("SPY", 500, -3.2, 28.5, 495, 510, 505, -1.8);

    @Test
    void parsesDecisionAndSendsSnapshot() {
        FakeModel model = new FakeModel("<think>x</think>{\"decision\":\"buy\",\"confidence\":0.8,\"reasoning\":\"과매도\"}");

        StockDecision d = new StockAgent(model).decide(SNAPSHOT);

        assertEquals("BUY", d.decision());
        assertEquals(0.8, d.confidence());
        assertTrue(model.requests.get(0).getUserPrompt().contains("\"rsi\":28.5"));
    }

    @Test
    void retriesOnceWhenDecisionInvalidThenFails() {
        FakeModel model = new FakeModel("{\"decision\":\"MAYBE\",\"confidence\":0.5,\"reasoning\":\"x\"}");

        assertThrows(AgentResponseException.class, () -> new StockAgent(model).decide(SNAPSHOT));
        assertEquals(2, model.requests.size());
    }

    @Test
    void rsiIs100WhenOnlyGainsAndSnapshotNeedsEnoughData() {
        List<Double> up = new ArrayList<>();
        for (int i = 0; i < 70; i++) up.add(100.0 + i);

        StockSnapshot s = Indicators.snapshot("UP", up);

        assertEquals(100.0, s.rsi());
        assertEquals(169.0, s.price());
        assertTrue(s.zscore20() > 1);
        assertThrows(IllegalArgumentException.class, () -> Indicators.snapshot("X", up.subList(0, 30)));
    }
}
