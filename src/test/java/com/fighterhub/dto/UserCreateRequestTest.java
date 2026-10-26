package com.fighterhub.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class UserCreateRequestTest {

    private static Validator newValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static UserCreateRequest withMessage(String message) {
        return new UserCreateRequest(
                "user@example.com",
                "password123",
                "Ryu",
                List.of(new UserCharacterRequest(1L, "MASTER", 1600)),
                null,
                null,
                message);
    }

    @Test
    void validate_messageがnullの場合_違反なし() {
        assertTrue(newValidator().validate(withMessage(null)).isEmpty());
    }

    @Test
    void validate_messageが1000文字の場合_違反なし() {
        assertTrue(newValidator().validate(withMessage("a".repeat(1000))).isEmpty());
    }

    @Test
    void validate_messageが1001文字の場合_違反になる() {
        assertFalse(newValidator().validate(withMessage("a".repeat(1001))).isEmpty());
    }
}
