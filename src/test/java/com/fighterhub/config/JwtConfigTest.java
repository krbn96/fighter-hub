package com.fighterhub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

// JwtConfig.resolveSecretKey(...)のvalidationロジックを、Spring Contextなしで検証する。
class JwtConfigTest {

    @Test
    void resolveSecretKey_有効なBase64で32byte以上の場合_SecretKeyを返す() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);

        SecretKey secretKey = JwtConfig.resolveSecretKey(secret);

        assertEquals("HmacSHA256", secretKey.getAlgorithm());
        assertEquals(32, secretKey.getEncoded().length);
    }

    @Test
    void resolveSecretKey_33byte以上でも成功する() {
        String secret = Base64.getEncoder().encodeToString(new byte[64]);

        SecretKey secretKey = JwtConfig.resolveSecretKey(secret);

        assertEquals(64, secretKey.getEncoded().length);
    }

    @Test
    void resolveSecretKey_secretがnullの場合_例外を投げる() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> JwtConfig.resolveSecretKey(null));

        assertTrue(exception.getMessage().contains("JWT secret configuration is invalid"));
    }

    @Test
    void resolveSecretKey_secretが空文字の場合_例外を投げる() {
        assertThrows(IllegalStateException.class, () -> JwtConfig.resolveSecretKey(""));
    }

    @Test
    void resolveSecretKey_secretが空白のみの場合_例外を投げる() {
        assertThrows(IllegalStateException.class, () -> JwtConfig.resolveSecretKey("   "));
    }

    @Test
    void resolveSecretKey_未解決のplaceholderの場合_例外を投げる() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> JwtConfig.resolveSecretKey("${JWT_SECRET}"));

        assertTrue(exception.getMessage().contains("unresolved"));
    }

    @Test
    void resolveSecretKey_Base64として不正な場合_例外を投げる() {
        assertThrows(
                IllegalStateException.class,
                () -> JwtConfig.resolveSecretKey("not-valid-base64!!!"));
    }

    @Test
    void resolveSecretKey_decode後32byte未満の場合_例外を投げる() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> JwtConfig.resolveSecretKey(shortSecret));

        assertTrue(exception.getMessage().contains("256 bits"));
    }

    @Test
    void resolveSecretKey_decode後31byteの場合も例外を投げる() {
        String almostEnoughSecret = Base64.getEncoder().encodeToString(new byte[31]);

        assertThrows(
                IllegalStateException.class,
                () -> JwtConfig.resolveSecretKey(almostEnoughSecret));
    }

    @Test
    void resolveSecretKey_例外メッセージにsecretの値そのものが含まれない() {
        String secretMarker = "super-secret-marker-value-should-not-leak";

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> JwtConfig.resolveSecretKey(secretMarker));

        assertFalse(exception.getMessage().contains(secretMarker));
    }
}
