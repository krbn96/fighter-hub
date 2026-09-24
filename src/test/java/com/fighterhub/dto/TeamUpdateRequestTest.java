package com.fighterhub.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import tools.jackson.databind.ObjectMapper;

// TeamUpdateRequest(PATCH /api/teams/{id}用DTO)のJackson3デシリアライズと
// Jakarta Bean Validationの実挙動を確認する。UserUpdateRequestTestと同じ観点で検証する。
@ExtendWith(SpringExtension.class)
@JsonTest
class TeamUpdateRequestTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static Validator newValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ==== デシリアライズ: {} で全フィールドがundefinedになること ====

    @Test
    void readValue_空オブジェクトの場合_全フィールドがundefinedになる() {
        TeamUpdateRequest request = objectMapper.readValue("{}", TeamUpdateRequest.class);

        assertTrue(request.name().isUndefined());
        assertTrue(request.rankRequirement().isUndefined());
        assertTrue(request.characterRequirements().isUndefined());
        assertTrue(request.recruitmentMessage().isUndefined());
    }

    // ==== デシリアライズ: 未指定と明示的nullを区別できること ====

    @Test
    void readValue_recruitmentMessageが未指定の場合_undefinedになる() {
        TeamUpdateRequest request = objectMapper.readValue(
                "{\"name\": \"Team Ryu\"}", TeamUpdateRequest.class);

        assertTrue(request.recruitmentMessage().isUndefined());
    }

    @Test
    void readValue_recruitmentMessageが明示的nullの場合_presentかつ値nullになる() {
        TeamUpdateRequest request = objectMapper.readValue(
                "{\"recruitmentMessage\": null}", TeamUpdateRequest.class);

        assertFalse(request.recruitmentMessage().isUndefined());
        assertTrue(request.recruitmentMessage().isPresent());
        assertEquals(null, request.recruitmentMessage().get());
    }

    @Test
    void readValue_characterRequirementsが明示的nullの場合_presentかつ値nullになる() {
        TeamUpdateRequest request = objectMapper.readValue(
                "{\"characterRequirements\": null}", TeamUpdateRequest.class);

        assertFalse(request.characterRequirements().isUndefined());
        assertTrue(request.characterRequirements().isPresent());
        assertEquals(null, request.characterRequirements().get());
    }

    @Test
    void readValue_characterRequirementsが空配列の場合_presentかつ空リストになる() {
        TeamUpdateRequest request = objectMapper.readValue(
                "{\"characterRequirements\": []}", TeamUpdateRequest.class);

        assertFalse(request.characterRequirements().isUndefined());
        assertTrue(request.characterRequirements().isPresent());
        assertTrue(request.characterRequirements().get().isEmpty());
    }

    @Test
    void readValue_characterRequirementsに値がある場合_presentかつそのリストになる() {
        TeamUpdateRequest request = objectMapper.readValue(
                "{\"characterRequirements\": [1, 2]}", TeamUpdateRequest.class);

        assertTrue(request.characterRequirements().isPresent());
        assertEquals(List.of(1L, 2L), request.characterRequirements().get());
    }

    // ==== コンストラクタ直接呼び出し時にnull参照がundefinedへ正規化されること ====

    @Test
    void new_JsonNullableを渡さずnull参照で構築した場合_undefinedへ正規化される() {
        TeamUpdateRequest request = new TeamUpdateRequest(null, null, null, null);

        assertTrue(request.name().isUndefined());
        assertTrue(request.rankRequirement().isUndefined());
        assertTrue(request.characterRequirements().isUndefined());
        assertTrue(request.recruitmentMessage().isUndefined());
    }

    // ==== Bean Validation: name ====

    @Test
    void validate_nameが未指定の場合_違反なし() {
        TeamUpdateRequest request = withName(JsonNullable.undefined());

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが明示的nullの場合_違反になる() {
        TeamUpdateRequest request = withName(JsonNullable.of(null));

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが空文字の場合_違反になる() {
        TeamUpdateRequest request = withName(JsonNullable.of(""));

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが255文字の場合_違反なし() {
        TeamUpdateRequest request = withName(JsonNullable.of("a".repeat(255)));

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが256文字の場合_違反になる() {
        TeamUpdateRequest request = withName(JsonNullable.of("a".repeat(256)));

        assertFalse(newValidator().validate(request).isEmpty());
    }

    // ==== Bean Validation: rankRequirement(任意項目、nullを許可) ====

    @Test
    void validate_rankRequirementが明示的nullの場合_違反なし() {
        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.of(null),
                JsonNullable.undefined(), JsonNullable.undefined());

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_rankRequirementが31文字の場合_違反になる() {
        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.of("a".repeat(31)),
                JsonNullable.undefined(), JsonNullable.undefined());

        assertFalse(newValidator().validate(request).isEmpty());
    }

    // ==== Bean Validation: characterRequirements(任意項目、nullを許可) ====

    @Test
    void validate_characterRequirementsが明示的nullの場合_違反なし() {
        TeamUpdateRequest request = withCharacterRequirements(JsonNullable.of(null));

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_characterRequirementsが空リストの場合_違反なし() {
        TeamUpdateRequest request = withCharacterRequirements(JsonNullable.of(List.of()));

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_characterRequirementsにnull要素が含まれる場合_カスケード検証で違反になる() {
        TeamUpdateRequest request = withCharacterRequirements(
                JsonNullable.of(java.util.Arrays.asList(1L, null)));

        Set<ConstraintViolation<TeamUpdateRequest>> violations = newValidator().validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void validate_characterRequirementsに0が含まれる場合_違反になる() {
        TeamUpdateRequest request = withCharacterRequirements(JsonNullable.of(List.of(0L)));

        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_characterRequirementsに負数が含まれる場合_違反になる() {
        TeamUpdateRequest request = withCharacterRequirements(JsonNullable.of(List.of(-1L)));

        assertFalse(newValidator().validate(request).isEmpty());
    }

    // ==== Bean Validation: recruitmentMessage(任意項目、nullを許可) ====

    @Test
    void validate_recruitmentMessageが明示的nullの場合_違反なし() {
        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.of(null));

        assertTrue(newValidator().validate(request).isEmpty());
    }

    // ==== ヘルパー ====

    private static TeamUpdateRequest withName(JsonNullable<String> name) {
        return new TeamUpdateRequest(
                name, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());
    }

    private static TeamUpdateRequest withCharacterRequirements(JsonNullable<List<Long>> characterRequirements) {
        return new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(),
                characterRequirements, JsonNullable.undefined());
    }
}
