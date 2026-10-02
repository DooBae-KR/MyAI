package com.personal.ai.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiTokenFilterTest {

    private int run(String token, String uri, String auth) throws Exception {
        var req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        if (auth != null) req.addHeader("Authorization", auth);
        var res = new MockHttpServletResponse();
        new ApiTokenFilter(token).doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void noTokenConfiguredAllowsEverything() throws Exception {
        assertThat(run("", "/api/x", null)).isEqualTo(200);
    }

    @Test
    void apiRequiresMatchingBearer() throws Exception {
        assertThat(run("s3cret", "/api/x", null)).isEqualTo(401);
        assertThat(run("s3cret", "/api/x", "Bearer wrong")).isEqualTo(401);
        assertThat(run("s3cret", "/api/x", "Bearer s3cret")).isEqualTo(200);
    }

    @Test
    void staticFilesStayPublicSoTheAppCanAskForTheToken() throws Exception {
        assertThat(run("s3cret", "/index.html", null)).isEqualTo(200);
    }
}
