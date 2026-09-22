package com.fighterhub.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class AuthLoginRequestTest {

    private static Validator newValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validate_正常なemailとpasswordの場合_違反なし() {
        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "password123");
        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_emailがnullの場合_違反になる() {
        AuthLoginRequest request = new AuthLoginRequest(null, "password123");
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_emailがblankの場合_違反になる() {
        AuthLoginRequest request = new AuthLoginRequest("   ", "password123");
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_email形式が不正な場合_違反になる() {
        AuthLoginRequest request = new AuthLoginRequest("invalid-email", "password123");
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_passwordがnullの場合_違反になる() {
        AuthLoginRequest request = new AuthLoginRequest("user@example.com", null);
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_passwordがblankの場合_違反になる() {
        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "   ");
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_passwordが8文字未満でもblankでなければ違反にならない() {
        AuthLoginRequest request = new AuthLoginRequest("user@example.com", "short");
        assertTrue(newValidator().validate(request).isEmpty());
    }
}
