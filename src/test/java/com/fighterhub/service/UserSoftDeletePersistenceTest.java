package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fighterhub.dto.AuthLoginRequest;
import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.exception.AuthenticationFailedException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.repository.UserRepository;

// delete_flag=trueのUserが、実PostgreSQL上でもfindByIdAndDeleteFlagFalse/
// findByEmailAndDeleteFlagFalseというSpring Data JPAの導出クエリによって
// 正しく検索対象から除外されることを確認する統合テスト。
// Mockitoベースの単体テスト(UserServiceTest/AuthServiceTest)では、
// リポジトリメソッドの戻り値をモックしているため、delete_flagカラムに対する
// 実際のSQL条件が正しく生成されているかまでは検証できない。
//
// User Entityにはdelete_flagを外部から変更するsetter/softDelete()を追加していない
// (本番APIをテスト都合で変更しないため)。delete_flag=trueへの変更はJdbcTemplateによる
// 直接のSQL UPDATEで行う。
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=" + UserSoftDeletePersistenceTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class UserSoftDeletePersistenceTest {

    // TEST_ONLY_JWT_SECRET: このテストのApplicationContext起動のためだけのダミー値
    // (Base64, 32byte, 全byteゼロ)。本番のJWT_SECRET環境変数とは無関係。
    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final String TEST_PASSWORD = "password123";

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long createdUserId;

    @AfterEach
    void cleanUp() {
        // 既存のローカルfighter_hub DBを使い回すため、テストデータを必ず削除する。
        // 既存の手動確認用User(test@example.com等)は一切触らない。
        if (createdUserId != null) {
            userRepository.deleteById(createdUserId);
            createdUserId = null;
        }
    }

    @Test
    void deleteFlagがtrueのユーザーは正しいemailpasswordでもAuthServiceのloginで認証できない() {
        Long characterId = anyExistingCharacterId();

        String email = "soft-delete-login-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest createRequest = new UserCreateRequest(
                email,
                TEST_PASSWORD,
                "Soft Delete Login Test User",
                List.of(new UserCharacterRequest(characterId, "MASTER", 1600)),
                null,
                null,
                "test"
        );

        UserCreateResponse created = userService.createUser(createRequest);
        createdUserId = created.id();

        AuthLoginRequest loginRequest = new AuthLoginRequest(email, TEST_PASSWORD);

        // ソフトデリート前は、同じemail/passwordで正常にログインできることを確認する。
        // これにより、後続の失敗が「passwordが誤っているから」ではないことを担保する。
        assertDoesNotThrow(() -> authService.login(loginRequest));

        softDelete(createdUserId);

        assertThrows(
                AuthenticationFailedException.class,
                () -> authService.login(loginRequest));
    }

    @Test
    void deleteFlagがtrueのユーザーIDはUserServiceのfindMeで取得できない() {
        Long characterId = anyExistingCharacterId();

        String email = "soft-delete-findme-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest createRequest = new UserCreateRequest(
                email,
                TEST_PASSWORD,
                "Soft Delete FindMe Test User",
                List.of(new UserCharacterRequest(characterId, "MASTER", 1600)),
                null,
                null,
                "test"
        );

        UserCreateResponse created = userService.createUser(createRequest);
        createdUserId = created.id();

        // ソフトデリート前は、userIdが実在するため取得できることを確認する。
        assertDoesNotThrow(() -> userService.findMe(createdUserId));

        softDelete(createdUserId);

        assertThrows(
                UserNotFoundException.class,
                () -> userService.findMe(createdUserId));
    }

    private Long anyExistingCharacterId() {
        List<Character> characters = characterRepository.findAll();
        assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないため統合テストをスキップする");
        return characters.get(0).getId();
    }

    private void softDelete(Long userId) {
        int updatedRows = jdbcTemplate.update(
                "UPDATE t_users SET delete_flag = true WHERE id = ?", userId);
        assertEquals(1, updatedRows);
    }
}
