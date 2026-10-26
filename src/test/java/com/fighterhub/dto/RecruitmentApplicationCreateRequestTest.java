package com.fighterhub.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class RecruitmentApplicationCreateRequestTest {

    private static Validator newValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validate_messageがnullの場合_違反なし() {
        RecruitmentApplicationCreateRequest request = new RecruitmentApplicationCreateRequest(null);

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_messageが1000文字の場合_違反なし() {
        RecruitmentApplicationCreateRequest request =
                new RecruitmentApplicationCreateRequest("a".repeat(1000));

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_messageが1001文字の場合_違反になる() {
        RecruitmentApplicationCreateRequest request =
                new RecruitmentApplicationCreateRequest("a".repeat(1001));

        assertFalse(newValidator().validate(request).isEmpty());
    }
}
