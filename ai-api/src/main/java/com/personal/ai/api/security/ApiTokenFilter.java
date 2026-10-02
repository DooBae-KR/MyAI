package com.personal.ai.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** APP_TOKEN이 설정되면 /api/** 에 Authorization: Bearer 토큰을 요구한다. 서버를 외부(폰 앱)에 열 때 사용. */
@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenFilter.class);

    private final byte[] expected;

    public ApiTokenFilter(@Value("${app.token:}") String token) {
        this.expected = token.isBlank() ? null : ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        if (expected == null) {
            log.warn("APP_TOKEN이 비어 있어 /api에 인증이 없습니다. 서버를 내 PC 밖에 공개한다면 반드시 APP_TOKEN을 설정하세요.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expected == null || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        boolean ok = auth != null && MessageDigest.isEqual(expected, auth.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            chain.doFilter(req, res);
        } else {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"message\":\"인증 토큰이 필요합니다.\"}");
        }
    }
}
