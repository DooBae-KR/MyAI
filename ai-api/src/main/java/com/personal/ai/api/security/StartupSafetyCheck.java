package com.personal.ai.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 앱을 내 PC 밖에 열면서(SERVER_ADDRESS가 loopback이 아님) APP_TOKEN을 안 정하면 /api가 인터넷에 그대로 열리므로 시작을 거부한다.
 * 같은 Wi-Fi에서만 잠깐 쓰는 등 정말 의도한 경우에는 APP_ALLOW_NO_TOKEN=true로 풀 수 있다.
 */
@Component
public class StartupSafetyCheck {

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "::1", "[::1]");

    public StartupSafetyCheck(@Value("${server.address:}") String address,
                              @Value("${app.token:}") String token,
                              @Value("${app.allow-no-token:false}") boolean allowNoToken) {
        check(address, token, allowNoToken);
    }

    static void check(String address, String token, boolean allowNoToken) {
        boolean exposed = !LOOPBACK.contains(address.trim().toLowerCase());
        if (exposed && token.isBlank() && !allowNoToken) {
            throw new IllegalStateException("서버가 외부에 열려 있는데(SERVER_ADDRESS=" + (address.isBlank() ? "(전체)" : address)
                    + ") APP_TOKEN이 비어 있습니다. 인터넷의 누구나 학습 데이터와 LLM 비용을 쓸 수 있게 되므로 시작하지 않습니다. "
                    + ".env 또는 배포 환경 변수에 APP_TOKEN=<길고 무작위한 값>을 설정하세요. "
                    + "(같은 Wi-Fi에서만 쓰는 등 정말 의도한 경우에만 APP_ALLOW_NO_TOKEN=true)");
        }
    }
}
