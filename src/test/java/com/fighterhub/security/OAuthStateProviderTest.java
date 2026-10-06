package com.fighterhub.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// OAuthStateProvider(Discordアカウント連携専用の署名付きstate)の単体テスト。
// Spring Contextは起動せず、テスト専用に都度生成したSecretKeyのみで完結させている。
// 本番用のOAUTH_STATE_SECRETはこのテスト内で一切生成・保存・出力しない。
class OAuthStateProviderTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    private SecretKey secretKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
        keyGenerator.init(256);
        secretKey = keyGenerator.generateKey();
    }

    private OAuthStateProvider newProvider(Instant now) {
        return new OAuthStateProvider(secretKey, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void generateAndVerify_正常な場合_userIdを復元できる() {
        OAuthStateProvider provider = newProvider(FIXED_INSTANT);

        String state = provider.generate(42L);
        OAuthStateProvider.StatePayload payload = provider.verify(state);

        assertEquals(42L, payload.userId());
        assertEquals(OAuthStateProvider.DISCORD_LINK_PURPOSE, payload.purpose());
    }

    @Test
    void generate_呼び出すたびに異なるnonceを含むstateを生成する() {
        OAuthStateProvider provider = newProvider(FIXED_INSTANT);

        String state1 = provider.generate(1L);
        String state2 = provider.generate(1L);

        assertNotEquals(state1, state2);
    }

    @Test
    void verify_署名部分が改ざんされている場合_OAuthStateInvalidExceptionを投げる() {
        OAuthStateProvider provider = newProvider(FIXED_INSTANT);
        String state = provider.generate(1L);

        String[] parts = state.split("\\.", 2);
        String tampered = parts[0] + "." + "tamperedSignatureXXXXXXXXXXXXXXXXXXXXXXXX";

        assertThrows(OAuthStateInvalidException.class, () -> provider.verify(tampered));
    }

    @Test
    void verify_payload部分が改ざんされている場合_OAuthStateInvalidExceptionを投げる() {
        OAuthStateProvider provider = newProvider(FIXED_INSTANT);
        String state = provider.generate(1L);

        String[] parts = state.split("\\.", 2);
        // payloadだけを他Userのstateのpayloadへ差し替える(署名は元のまま)。
        // 署名はpayload全体に対して計算されているため、不整合として検出されるはず。
        String otherState = provider.generate(2L);
        String otherPayload = otherState.split("\\.", 2)[0];
        String tampered = otherPayload + "." + parts[1];

        assertThrows(OAuthStateInvalidException.class, () -> provider.verify(tampered));
    }

    @Test
    void verify_形式が不正な場合_OAuthStateInvalidExceptionを投げる() {
        OAuthStateProvider provider = newProvider(FIXED_INSTANT);

        assertThrows(OAuthStateInvalidException.class, () -> provider.verify("not-a-valid-state"));
    }

    @Test
    void verify_stateがnullの場合_OAuthStateInvalidExceptionを投げる() {
        OAuthStateProvider provider = newProvider(FIXED_INSTANT);

        assertThrows(OAuthStateInvalidException.class, () -> provider.verify(null));
    }

    @Test
    void verify_5分以内の場合_期限切れにならない() {
        OAuthStateProvider generateProvider = newProvider(FIXED_INSTANT);
        String state = generateProvider.generate(1L);

        OAuthStateProvider verifyProvider = newProvider(FIXED_INSTANT.plusSeconds(4 * 60 + 59));

        OAuthStateProvider.StatePayload payload = verifyProvider.verify(state);
        assertEquals(1L, payload.userId());
    }

    @Test
    void verify_5分を過ぎている場合_OAuthStateExpiredExceptionを投げる() {
        OAuthStateProvider generateProvider = newProvider(FIXED_INSTANT);
        String state = generateProvider.generate(1L);

        OAuthStateProvider verifyProvider = newProvider(FIXED_INSTANT.plusSeconds(5 * 60));

        assertThrows(OAuthStateExpiredException.class, () -> verifyProvider.verify(state));
    }
}
