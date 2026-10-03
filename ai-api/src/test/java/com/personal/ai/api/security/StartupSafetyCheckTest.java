package com.personal.ai.api.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StartupSafetyCheckTest {

    @Test
    void loopbackNeedsNoToken() {
        for (String address : new String[] {"127.0.0.1", "localhost", "::1", " LOCALHOST "}) {
            assertDoesNotThrow(() -> StartupSafetyCheck.check(address, "", false), address);
        }
    }

    @Test
    void exposedServerWithoutTokenRefusesToStartAndExplainsHow() {
        for (String address : new String[] {"0.0.0.0", "", "192.168.0.5", "::"}) {
            var e = assertThrows(IllegalStateException.class, () -> StartupSafetyCheck.check(address, "  ", false), address);
            assertTrue(e.getMessage().contains("APP_TOKEN"));
        }
    }

    @Test
    void exposedServerWithTokenOrExplicitOverrideStarts() {
        assertDoesNotThrow(() -> StartupSafetyCheck.check("0.0.0.0", "a-long-random-token", false));
        assertDoesNotThrow(() -> StartupSafetyCheck.check("0.0.0.0", "", true));
    }
}
