package com.personal.ai.api;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.util.Locale;

/**
 * 시작할 때 DB 접속 설정이 어긋난 흔한 경우(Supabase)를 알아보기 쉬운 문장으로 안내한다.
 * 비밀 값이 새지 않도록 예외 메시지나 설정 값은 출력하지 않고 고정 문장만 쓴다.
 */
public class DbConnectionFailureAnalyzer extends AbstractFailureAnalyzer<Throwable> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, Throwable cause) {
        String chain = messageChain(rootFailure);

        if (chain.contains("must start with \"jdbc") || chain.contains("must start with 'jdbc")) {
            return result("DB_URL이 비어 있거나 JDBC 형식이 아닙니다.",
                    "DB_URL은 jdbc:postgresql://<호스트>:<포트>/postgres 형식이어야 합니다. Supabase Connect 화면에 보이는 "
                            + "postgresql://사용자:비밀번호@호스트:포트/postgres 주소를 그대로 넣으면 안 됩니다. 앞에 jdbc:를 붙이고 "
                            + "사용자:비밀번호@ 부분은 빼서, 사용자는 DB_USERNAME, 비밀번호는 DB_PASSWORD에 따로 넣으세요 "
                            + "(예: jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres). "
                            + "값이 아예 없다면 .env를 읽지 못한 것이니 .env 위치(프로젝트 루트)와 실행 위치를 확인하세요.", rootFailure);
        }
        if (chain.contains("could not resolve placeholder 'db_")) {
            return result("DB 접속 정보(DB_URL, DB_USERNAME, DB_PASSWORD)를 찾지 못했습니다.",
                    "프로젝트 루트의 .env에 세 값이 있는지 확인하세요. 실행 위치가 프로젝트 루트나 ai-api 폴더가 아니면 .env를 읽지 못하니, "
                            + "그 경우 IDE 실행 설정의 환경변수에 직접 넣으세요. 값에 따옴표를 붙이지 마세요.", rootFailure);
        }
        if (chain.contains("tenant or user not found")) {
            return result("Supabase가 접속한 주소에서 이 사용자나 프로젝트를 찾지 못했습니다. DB 주소와 사용자 이름이 서로 맞지 않는 경우입니다.",
                    "풀러 주소(…pooler.supabase.com)는 사용자 이름이 postgres.<프로젝트-ref>, 직접 연결 주소(db.<ref>.supabase.co)는 postgres여야 합니다. "
                            + "Supabase 대시보드의 Connect 화면에서 주소와 사용자 이름을 한 쌍으로 복사해 DB_URL, DB_USERNAME에 넣으세요. "
                            + "프로젝트가 일시 중지(pause)됐는지도 확인하세요.", rootFailure);
        }
        if (chain.contains("password authentication failed")) {
            return result("DB 비밀번호가 맞지 않습니다.",
                    "Supabase에서 비밀번호를 재설정했다면 .env의 DB_PASSWORD도 같은 값으로 바꾸세요. 값에 따옴표나 앞뒤 공백이 없어야 합니다.", rootFailure);
        }
        if (chain.contains("unknownhostexception") || chain.contains("network is unreachable")
                || (chain.contains("postgresql") && (chain.contains("connection refused") || chain.contains("timed out")))) {
            return result("DB 서버에 연결하지 못했습니다. 주소가 틀렸거나 네트워크가 막혀 있습니다.",
                    "DB_URL의 호스트와 포트를 확인하세요. 직접 연결 주소는 IPv6 전용이라 IPv4 네트워크에서는 닿지 않을 수 있으니, "
                            + "그 경우 Connect 화면의 Session pooler 주소(…pooler.supabase.com:5432)를 쓰세요.", rootFailure);
        }
        if (chain.contains("checksum mismatch") || chain.contains("detected applied migration not resolved locally")
                || chain.contains("migrations have failed validation")) {
            return result("DB에 기록된 마이그레이션 이력과 이 앱의 마이그레이션이 일치하지 않습니다.",
                    "personal_ai 스키마가 다른 버전의 앱으로 만들어진 경우입니다. 개발용 DB이고 지워도 되는 데이터라면 "
                            + "DROP SCHEMA personal_ai CASCADE; 후 다시 실행하세요(그 스키마의 학습 데이터가 모두 삭제됩니다). "
                            + "지우면 안 되는 데이터라면 마이그레이션 파일을 수정하지 말고 새 버전(V4…)으로 추가해야 합니다.", rootFailure);
        }
        if (chain.contains("permission denied for") || chain.contains("must be owner")) {
            return result("DB 계정에 스키마를 만들거나 쓸 권한이 없습니다.",
                    "접속 계정이 personal_ai 스키마를 만들 수 있어야 합니다. Supabase의 SQL Editor에서 스키마를 미리 만들고 "
                            + "해당 계정에 권한을 부여하세요.", rootFailure);
        }
        return null; // 이 분석기가 아는 경우가 아니면 기본 안내를 그대로 쓴다
    }

    private static FailureAnalysis result(String description, String action, Throwable cause) {
        return new FailureAnalysis(description, action, cause);
    }

    private static String messageChain(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            text.append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append(' ');
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }
}
