package com.fighterhub.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

// FIGHTER HUBのJWT subはUser.id(正のLong値)を文字列化したもの、という前提を検証する。
// 署名・exp検証は別のvalidator(JwtTimestampValidator等)が担うため、ここではsubjectの
// 形式のみを見る。前提を満たさないJWTは、署名やexpが正しくても認証失敗として扱う。
public class JwtSubjectValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SUBJECT = new OAuth2Error(
            "invalid_token",
            "The sub claim must be a positive Long value.",
            null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String subject = token.getSubject();

        if (subject == null || subject.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }

        long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }

        if (userId <= 0) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }

        return OAuth2TokenValidatorResult.success();
    }
}
