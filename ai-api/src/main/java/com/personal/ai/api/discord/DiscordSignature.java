package com.personal.ai.api.discord;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Discord Interactions 요청의 Ed25519 서명 검증. 서명 대상은 timestamp + 요청 본문(바이트 그대로)이다.
 * 오래된 타임스탬프는 재전송 공격을 막으려고 거절한다.
 */
final class DiscordSignature {

    static final Duration MAX_AGE = Duration.ofMinutes(5);
    /** Ed25519 공개키(32바이트)를 X.509 SubjectPublicKeyInfo로 감싸는 고정 접두사. */
    private static final byte[] SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    private final PublicKey key;

    /** @param publicKeyHex Discord 개발자 포털의 Public Key(64자리 16진수) */
    DiscordSignature(String publicKeyHex) {
        try {
            byte[] raw = HexFormat.of().parseHex(publicKeyHex.trim());
            if (raw.length != 32) {
                throw new IllegalArgumentException("공개키는 32바이트(64자리 16진수)여야 합니다.");
            }
            byte[] spki = new byte[SPKI_PREFIX.length + raw.length];
            System.arraycopy(SPKI_PREFIX, 0, spki, 0, SPKI_PREFIX.length);
            System.arraycopy(raw, 0, spki, SPKI_PREFIX.length, raw.length);
            this.key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalArgumentException("공개키를 읽을 수 없습니다.", e);
        }
    }

    boolean verify(String signatureHex, String timestamp, String body, Instant now) {
        if (signatureHex == null || timestamp == null || body == null) {
            return false;
        }
        try {
            long seconds = Long.parseLong(timestamp);
            if (Duration.between(Instant.ofEpochSecond(seconds), now).abs().compareTo(MAX_AGE) > 0) {
                return false;
            }
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(key);
            sig.update(timestamp.getBytes(StandardCharsets.UTF_8));
            sig.update(body.getBytes(StandardCharsets.UTF_8));
            return sig.verify(HexFormat.of().parseHex(signatureHex));
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) { // 숫자/16진수 형식 오류 포함
            return false;
        }
    }
}
