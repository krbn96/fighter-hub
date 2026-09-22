package com.fighterhub.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.fighterhub.config.JwtProperties;

// JwtProvider(JWT生成)の単体テスト。Spring Contextは起動せず、Clock.fixed(...)と
// テスト専用に都度生成したSecretKeyのみで完結させている。本番用の秘密鍵は
// このテスト内で一切生成・保存・出力しない。
class JwtProviderTest {

    // NimbusJwtDecoderはデフォルトで実際の壁時計時刻に対してexpを検証するため、
    // 過去日時で固定するとdecode時に必ず「期限切れ」となってしまう。
    // JwtProvider自身のClock注入によるテスト容易性は維持しつつ、
    // decoderの検証に影響されないよう実行時点のInstant.now()を基準に固定する。
    // JWTのiat/expはNumericDate(秒単位の整数)で表現されるため、
    // サブ秒精度が失われる。比較のずれを防ぐため秒単位へ切り詰める。
    private static final Instant FIXED_INSTANT = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Duration EXPIRATION = Duration.ofHours(24);

    private SecretKey secretKey;
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
        keyGenerator.init(256);
        secretKey = keyGenerator.generateKey();

        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();

        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        JwtProperties jwtProperties = new JwtProperties("unused-in-provider", EXPIRATION);

        jwtProvider = new JwtProvider(jwtEncoder, jwtProperties, fixedClock);
    }

    @Test
    void generateToken_トークン生成に成功する() {
        String token = jwtProvider.generateToken(1L);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateToken_subjectがuserIdの文字列表現と一致する() {
        String token = jwtProvider.generateToken(42L);

        Jwt decoded = decodeWithKey(token, secretKey);

        assertEquals("42", decoded.getSubject());
    }

    @Test
    void generateToken_issuedAtが固定Clockの時刻と一致する() {
        String token = jwtProvider.generateToken(1L);

        Jwt decoded = decodeWithKey(token, secretKey);

        assertEquals(FIXED_INSTANT, decoded.getIssuedAt());
    }

    @Test
    void generateToken_expiresAtがissuedAtプラス24時間と一致する() {
        String token = jwtProvider.generateToken(1L);

        Jwt decoded = decodeWithKey(token, secretKey);

        assertEquals(FIXED_INSTANT.plus(EXPIRATION), decoded.getExpiresAt());
    }

    @Test
    void generateToken_expとiatの差がちょうど24時間である() {
        String token = jwtProvider.generateToken(1L);

        Jwt decoded = decodeWithKey(token, secretKey);

        Duration actualDuration = Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt());
        assertEquals(EXPIRATION, actualDuration);
    }

    @Test
    void generateToken_email_name_roleクレームが含まれていない() {
        String token = jwtProvider.generateToken(1L);

        Jwt decoded = decodeWithKey(token, secretKey);

        assertFalse(decoded.getClaims().containsKey("email"));
        assertFalse(decoded.getClaims().containsKey("name"));
        assertFalse(decoded.getClaims().containsKey("role"));
        assertTrue(decoded.getClaims().containsKey("sub"));
        assertTrue(decoded.getClaims().containsKey("iat"));
        assertTrue(decoded.getClaims().containsKey("exp"));
    }

    @Test
    void generateToken_正しいSecretKeyで署名を検証できる() {
        String token = jwtProvider.generateToken(1L);

        Jwt decoded = decodeWithKey(token, secretKey);

        assertNotNull(decoded);
    }

    @Test
    void generateToken_別のSecretKeyでは署名検証に失敗する() throws Exception {
        String token = jwtProvider.generateToken(1L);

        KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
        keyGenerator.init(256);
        SecretKey differentKey = keyGenerator.generateKey();

        JwtDecoder decoderWithDifferentKey = NimbusJwtDecoder.withSecretKey(differentKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        assertThrows(JwtException.class, () -> decoderWithDifferentKey.decode(token));
    }

    private static Jwt decodeWithKey(String token, SecretKey key) {
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        return decoder.decode(token);
    }
}
