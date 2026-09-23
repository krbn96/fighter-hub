package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.dto.UserMeResponse;
import com.fighterhub.dto.UserUpdateRequest;
import com.fighterhub.entity.Character;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.repository.UserRepository;

// updateUser()がdirty checkingのみでDBへ永続化されること、および
// @UpdateTimestampによるupdatedAtがPATCH Response自身にも正しく反映されることを確認する統合テスト。
// OSIVが無効なため、updateUserとfindMeを別々にService呼び出しすることで、
// 別トランザクション/別EntityManagerでの再取得によるDB確認となる。
// Mockitoベースの単体テストでは検出できない永続化・flushタイミング不具合をここで検出する。
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=" + UserServicePersistenceTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class UserServicePersistenceTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    private Long createdUserId;

    @AfterEach
    void cleanUp() {
        if (createdUserId != null) {
            userRepository.deleteById(createdUserId);
            createdUserId = null;
        }
    }

    @Test
    void updateUser_messageを変更した場合_PATCH_Responseと再取得の両方で新しいupdatedAtとmessageが返る() {
        List<Character> characters = characterRepository.findAll();
        assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないため統合テストをスキップする");

        Long characterId = characters.get(0).getId();

        UserCreateRequest createRequest = new UserCreateRequest(
                "persistence-test-" + UUID.randomUUID() + "@example.com",
                "password123",
                "Persistence Test User",
                List.of(new UserCharacterRequest(characterId, "MASTER", 1600)),
                null,
                null,
                "よろしくお願いします"
        );

        UserCreateResponse created = userService.createUser(createRequest);
        createdUserId = created.id();

        UserMeResponse beforeUpdate = userService.findMe(createdUserId);
        LocalDateTime updatedAtBeforePatch = beforeUpdate.updatedAt();

        UserUpdateRequest updateRequest = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of("FIGHTER HUBを開発中です"),
                JsonNullable.undefined(),
                JsonNullable.undefined()
        );

        UserMeResponse patchResponse = userService.updateUser(createdUserId, updateRequest);
        assertEquals("FIGHTER HUBを開発中です", patchResponse.message());
        // flush前にレスポンスを生成すると@UpdateTimestampが未反映のまま返ってしまう回帰を防ぐ。
        assertNotEquals(updatedAtBeforePatch, patchResponse.updatedAt());

        // updateUser()とは別のService呼び出し(OSIV無効のため別トランザクション・別EntityManager)で
        // DBから再取得し、dirty checkingによる永続化を確認する。
        UserMeResponse afterPatch = userService.findMe(createdUserId);

        assertEquals("FIGHTER HUBを開発中です", afterPatch.message());
        assertEquals(patchResponse.updatedAt(), afterPatch.updatedAt());
        assertNotEquals(updatedAtBeforePatch, afterPatch.updatedAt());
    }
}
