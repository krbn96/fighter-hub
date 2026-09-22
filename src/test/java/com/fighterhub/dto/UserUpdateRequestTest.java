package com.fighterhub.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
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

// UserUpdateRequest(PATCH /api/users/me用DTO)のJackson3デシリアライズとJakarta Bean Validationの実挙動を確認する。
// Service/Entity/Controller側の更新処理はまだ実装していない。
@ExtendWith(SpringExtension.class)
@JsonTest
class UserUpdateRequestTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static Validator newValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ==== デシリアライズ: {} で全フィールドがundefinedになること ====

    @Test
    void readValue_空オブジェクトの場合_全フィールドがundefinedになる() {
        UserUpdateRequest request = objectMapper.readValue("{}", UserUpdateRequest.class);

        assertTrue(request.name().isUndefined());
        assertTrue(request.characters().isUndefined());
        assertTrue(request.playTimeStart().isUndefined());
        assertTrue(request.playTimeEnd().isUndefined());
        assertTrue(request.message().isUndefined());
        assertTrue(request.xId().isUndefined());
        assertTrue(request.discordId().isUndefined());
    }

    // ==== デシリアライズ: nullable項目で未指定と明示的nullを区別できること ====

    @Test
    void readValue_messageが未指定の場合_undefinedになる() {
        UserUpdateRequest request = objectMapper.readValue(
                "{\"name\": \"Ryu\", \"characters\": [{\"characterId\": 1, \"rank\": \"MASTER\"}]}",
                UserUpdateRequest.class);

        assertTrue(request.message().isUndefined());
    }

    @Test
    void readValue_messageが明示的nullの場合_presentかつ値nullになる() {
        UserUpdateRequest request = objectMapper.readValue(
                "{\"message\": null}",
                UserUpdateRequest.class);

        assertFalse(request.message().isUndefined());
        assertTrue(request.message().isPresent());
        assertEquals(null, request.message().get());
    }

    @Test
    void readValue_messageが値ありの場合_presentかつその値になる() {
        UserUpdateRequest request = objectMapper.readValue(
                "{\"message\": \"hello\"}",
                UserUpdateRequest.class);

        assertFalse(request.message().isUndefined());
        assertTrue(request.message().isPresent());
        assertEquals("hello", request.message().get());
    }

    @Test
    void readValue_playTimeStartが値ありの場合_LocalTimeとして取得できる() {
        UserUpdateRequest request = objectMapper.readValue(
                "{\"playTimeStart\": \"20:00\"}",
                UserUpdateRequest.class);

        assertTrue(request.playTimeStart().isPresent());
        assertEquals(LocalTime.of(20, 0), request.playTimeStart().get());
    }

    // ==== コンストラクタ直接呼び出し時にnull参照がundefinedへ正規化されること ====

    @Test
    void new_JsonNullableを渡さずnull参照で構築した場合_undefinedへ正規化される() {
        UserUpdateRequest request = new UserUpdateRequest(
                null, null, null, null, null, null, null);

        assertTrue(request.name().isUndefined());
        assertTrue(request.characters().isUndefined());
        assertTrue(request.playTimeStart().isUndefined());
        assertTrue(request.playTimeEnd().isUndefined());
        assertTrue(request.message().isUndefined());
        assertTrue(request.xId().isUndefined());
        assertTrue(request.discordId().isUndefined());
    }

    // ==== Bean Validation: name ====

    @Test
    void validate_nameが未指定の場合_違反なし() {
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(), validCharacters(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが明示的nullの場合_違反になる() {
        UserUpdateRequest request = withName(JsonNullable.of(null));
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが空文字の場合_違反になる() {
        UserUpdateRequest request = withName(JsonNullable.of(""));
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが空白のみの場合_違反になる() {
        UserUpdateRequest request = withName(JsonNullable.of("   "));
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが50文字の場合_違反なし() {
        UserUpdateRequest request = withName(JsonNullable.of("a".repeat(50)));
        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが51文字の場合_違反になる() {
        UserUpdateRequest request = withName(JsonNullable.of("a".repeat(51)));
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_nameが正常値の場合_違反なし() {
        UserUpdateRequest request = withName(JsonNullable.of("Ryu"));
        assertTrue(newValidator().validate(request).isEmpty());
    }

    // ==== Bean Validation: characters ====

    @Test
    void validate_charactersが未指定の場合_違反なし() {
        UserUpdateRequest request = withCharacters(JsonNullable.undefined());
        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_charactersが明示的nullの場合_違反になる() {
        UserUpdateRequest request = withCharacters(JsonNullable.of(null));
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_charactersが0件の場合_違反になる() {
        UserUpdateRequest request = withCharacters(JsonNullable.of(List.of()));
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_charactersが1件の場合_違反なし() {
        UserUpdateRequest request = withCharacters(JsonNullable.of(
                List.of(new UserCharacterRequest(1L, "MASTER", 1600))));
        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_charactersが4件の場合_違反なし() {
        UserUpdateRequest request = withCharacters(JsonNullable.of(List.of(
                new UserCharacterRequest(1L, "MASTER", 1600),
                new UserCharacterRequest(2L, "MASTER", 1600),
                new UserCharacterRequest(3L, "MASTER", 1600),
                new UserCharacterRequest(4L, "MASTER", 1600)
        )));
        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_charactersが5件の場合_違反になる() {
        UserUpdateRequest request = withCharacters(JsonNullable.of(List.of(
                new UserCharacterRequest(1L, "MASTER", 1600),
                new UserCharacterRequest(2L, "MASTER", 1600),
                new UserCharacterRequest(3L, "MASTER", 1600),
                new UserCharacterRequest(4L, "MASTER", 1600),
                new UserCharacterRequest(5L, "MASTER", 1600)
        )));
        assertFalse(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_characterIdがnullの場合_カスケード検証で違反になる() {
        UserUpdateRequest request = withCharacters(JsonNullable.of(
                List.of(new UserCharacterRequest(null, "MASTER", 1600))));

        Set<ConstraintViolation<UserUpdateRequest>> violations = newValidator().validate(request);

        assertFalse(violations.isEmpty());
        assertEquals("characters[0].characterId", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void validate_rankがnullの場合_カスケード検証で違反になる() {
        UserUpdateRequest request = withCharacters(JsonNullable.of(
                List.of(new UserCharacterRequest(1L, null, 1600))));

        Set<ConstraintViolation<UserUpdateRequest>> violations = newValidator().validate(request);

        assertFalse(violations.isEmpty());
        assertEquals("characters[0].rank", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void validate_rankが空文字の場合_カスケード検証で違反になる() {
        UserUpdateRequest request = withCharacters(JsonNullable.of(
                List.of(new UserCharacterRequest(1L, "", 1600))));

        Set<ConstraintViolation<UserUpdateRequest>> violations = newValidator().validate(request);

        assertFalse(violations.isEmpty());
        assertEquals("characters[0].rank", violations.iterator().next().getPropertyPath().toString());
    }

    // ==== Bean Validation: nullable項目(playTimeStart等)はnullを拒否しないこと ====

    @Test
    void validate_playTimeStartが明示的nullの場合_違反なし() {
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(), validCharacters(), JsonNullable.of(null),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        assertTrue(newValidator().validate(request).isEmpty());
    }

    @Test
    void validate_messageが明示的nullの場合_違反なし() {
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(), validCharacters(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.of(null), JsonNullable.undefined(), JsonNullable.undefined());

        assertTrue(newValidator().validate(request).isEmpty());
    }

    // ==== ヘルパー ====

    private static JsonNullable<List<UserCharacterRequest>> validCharacters() {
        return JsonNullable.of(List.of(new UserCharacterRequest(1L, "MASTER", 1600)));
    }

    private static UserUpdateRequest withName(JsonNullable<String> name) {
        return new UserUpdateRequest(
                name, validCharacters(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());
    }

    private static UserUpdateRequest withCharacters(JsonNullable<List<UserCharacterRequest>> characters) {
        return new UserUpdateRequest(
                JsonNullable.undefined(), characters, JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());
    }
}
