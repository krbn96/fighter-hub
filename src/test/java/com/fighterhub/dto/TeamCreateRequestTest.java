package com.fighterhub.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class TeamCreateRequestTest {

    private static Validator newValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static TeamCreateRequest withCharacterRequirements(List<Long> characterRequirements) {
        return new TeamCreateRequest(
                1L, "Team Ryu", "MASTER", characterRequirements, "誰でも歓迎です");
    }

    @Test
    void validate_全項目が正常な場合_違反なし() {
        TeamCreateRequest request = withCharacterRequirements(List.of(1L, 2L));

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_任意項目がすべてnullの場合_違反なし() {
        TeamCreateRequest request = new TeamCreateRequest(1L, "Team Ryu", null, null, null);

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_characterRequirementsが空リストの場合_違反なし() {
        TeamCreateRequest request = withCharacterRequirements(List.of());

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_tournamentIdがnullの場合_違反になる() {
        TeamCreateRequest request = new TeamCreateRequest(
                null, "Team Ryu", null, null, null);

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameがnullの場合_違反になる() {
        TeamCreateRequest request = new TeamCreateRequest(
                1L, null, null, null, null);

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが空文字の場合_違反になる() {
        TeamCreateRequest request = new TeamCreateRequest(
                1L, "", null, null, null);

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが256文字の場合_違反になる() {
        TeamCreateRequest request = new TeamCreateRequest(
                1L, "a".repeat(256), null, null, null);

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが255文字の場合_違反なし() {
        TeamCreateRequest request = new TeamCreateRequest(
                1L, "a".repeat(255), null, null, null);

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_rankRequirementが31文字の場合_違反になる() {
        TeamCreateRequest request = new TeamCreateRequest(
                1L, "Team Ryu", "a".repeat(31), null, null);

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_characterRequirementsにnull要素が含まれる場合_違反になる() {
        TeamCreateRequest request = withCharacterRequirements(Arrays.asList(1L, null));

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_characterRequirementsに0が含まれる場合_違反になる() {
        TeamCreateRequest request = withCharacterRequirements(List.of(0L));

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_characterRequirementsに負数が含まれる場合_違反になる() {
        TeamCreateRequest request = withCharacterRequirements(List.of(-1L));

        assertFalse(newValidator().validate(request).isEmpty());
    }
}
