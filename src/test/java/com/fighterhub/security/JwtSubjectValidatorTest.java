package com.fighterhub.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

// JwtSubjectValidator(JwtDecoderのvalidatorとして合成する、subject形式検証)の単体テスト。
// Spring Contextは起動せず、Jwtオブジェクトを直接構築して検証ロジックのみを確認する。
class JwtSubjectValidatorTest {

    private final JwtSubjectValidator validator = new JwtSubjectValidator();

    @Test
    void validate_subjectが正のLong文字列の場合_成功する() {
        Jwt jwt = jwtWithSubject("1");

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertFalse(result.hasErrors());
    }

    @Test
    void validate_subjectが複数桁の正のLong文字列の場合_成功する() {
        Jwt jwt = jwtWithSubject("123");

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertFalse(result.hasErrors());
    }

    @Test
    void validate_subjectクレーム自体が存在しない場合_失敗する() {
        Jwt jwt = jwtWithoutSubject();

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1.5", "0", "-1", "99999999999999999999"})
    void validate_不正なsubjectの場合_失敗する(String subject) {
        Jwt jwt = jwtWithSubject(subject);

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
    }

    private static Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private static Jwt jwtWithoutSubject() {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("iat", Instant.now(), "exp", Instant.now().plusSeconds(3600)));
    }
}
