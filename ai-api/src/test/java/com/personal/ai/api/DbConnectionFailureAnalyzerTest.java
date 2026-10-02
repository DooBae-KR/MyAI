package com.personal.ai.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.net.UnknownHostException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DbConnectionFailureAnalyzerTest {

    private final DbConnectionFailureAnalyzer analyzer = new DbConnectionFailureAnalyzer();

    private FailureAnalysis analyze(Throwable root) {
        return analyzer.analyze(root);
    }

    @Test
    void explainsUserHostMismatchForSupabasePooler() {
        FailureAnalysis a = analyze(new IllegalStateException("start failed",
                new SQLException("FATAL: Tenant or user not found")));

        assertNotNull(a);
        assertTrue(a.getAction().contains("postgres.<프로젝트-ref>"));
        assertTrue(a.getAction().contains("db.<ref>.supabase.co"));
    }

    @Test
    void explainsMissingProjectRefInPoolerUsername() {
        // Supabase 풀러에 postgres(ref 없음)로 접속하면 나오는 실제 메시지
        FailureAnalysis a = analyze(new IllegalStateException("Unable to obtain connection from database",
                new SQLException("FATAL: (ENOIDENTIFIER) no tenant identifier provided (external_id or sni_hostname required)")));

        assertNotNull(a);
        assertTrue(a.getAction().contains("postgres.<프로젝트-ref>"));
        assertTrue(a.getAction().contains("ref가 빠진"));
    }

    @Test
    void explainsWrongPasswordMissingEnvAndUnreachableHost() {
        assertTrue(analyze(new SQLException("FATAL: password authentication failed for user \"postgres\""))
                .getDescription().contains("비밀번호"));
        assertTrue(analyze(new IllegalArgumentException("Could not resolve placeholder 'DB_USERNAME' in value \"${DB_USERNAME}\""))
                .getAction().contains(".env"));
        assertTrue(analyze(new RuntimeException("Unable to open JDBC Connection", new UnknownHostException("db.x.supabase.co")))
                .getAction().contains("Session pooler"));
    }

    @Test
    void explainsNonJdbcUrlLikeSupabaseUriOrUnresolvedPlaceholder() {
        // 설정이 없으면 ${DB_URL} 문자열이, Supabase URI를 붙여넣으면 postgresql://... 가 url이 되어 같은 오류가 난다
        FailureAnalysis a = analyze(new RuntimeException("Failed to instantiate [HikariDataSource]",
                new IllegalArgumentException("'url' must start with \"jdbc\"")));

        assertTrue(a.getDescription().contains("JDBC 형식"));
        assertTrue(a.getAction().contains("jdbc:postgresql://"));
        assertTrue(a.getAction().contains("DB_USERNAME"));
    }

    @Test
    void explainsFlywayMigrationMismatch() {
        FailureAnalysis a = analyze(new IllegalStateException(
                "Validate failed: Migrations have failed validation. Migration checksum mismatch for migration version 1"));

        assertTrue(a.getAction().contains("DROP SCHEMA personal_ai CASCADE"));
        assertTrue(a.getAction().contains("V4"));
    }

    @Test
    void neverEchoesExceptionMessageAndIgnoresUnrelatedFailures() {
        FailureAnalysis a = analyze(new SQLException("FATAL: password authentication failed for user \"postgres\" secret-xyz"));

        assertFalse(a.getDescription().contains("secret-xyz"));
        assertFalse(a.getAction().contains("secret-xyz"));
        assertNull(analyze(new IllegalStateException("Port 8080 was already in use")));
    }
}
