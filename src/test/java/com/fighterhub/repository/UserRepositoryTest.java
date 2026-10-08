package com.fighterhub.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.entity.User;
import com.fighterhub.service.UserService;

// UserRepositoryのlock付き取得(findByIdAndDeleteFlagFalseForUpdate)が、実PostgreSQL上でも
// 既存のfindByIdAndDeleteFlagFalseと同じdeleteFlag条件で取得できることを確認する統合テスト。
// 既存のTeamRepositoryTest等と同じ、ローカルPostgreSQLへ直接接続する
// @SpringBootTest方式・手動cleanup方式をそのまま踏襲している。
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=" + UserRepositoryTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class UserRepositoryTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(userRepository::deleteById);
    }

    // PESSIMISTIC_WRITEロックの取得にはアクティブなTransactionが必須のため、
    // 本番でのService呼び出し(@Transactional内)を模して、テストメソッド自体を@Transactionalにする。
    @Test
    @Transactional
    void findByIdAndDeleteFlagFalseForUpdate_有効なUserをlock付きで取得でき無効なUserは取得できない() {
        Long characterId = anyExistingCharacterId();
        User user = createUser(characterId);

        Optional<User> found = userRepository.findByIdAndDeleteFlagFalseForUpdate(user.getId());

        assertTrue(found.isPresent());
        assertEquals(user.getId(), found.get().getId());

        jdbcTemplate.update("UPDATE t_users SET delete_flag = true WHERE id = ?", user.getId());

        assertTrue(userRepository.findByIdAndDeleteFlagFalseForUpdate(user.getId()).isEmpty());
    }

    // Day 7品質調査で指摘された、論理削除Userがdiscord_idを保持したまま残る場合の
    // 再連携不整合への対応。findByDiscordIdはdelete_flag条件を付けないため、
    // 論理削除済みのUserも検出できることを確認する。
    @Test
    void findByDiscordId_論理削除されたUserもdiscordIdで検出できる() {
        Long characterId = anyExistingCharacterId();
        User user = createUser(characterId);
        jdbcTemplate.update(
                "UPDATE t_users SET discord_id = ? WHERE id = ?", "discord-deleted-holder", user.getId());
        jdbcTemplate.update("UPDATE t_users SET delete_flag = true WHERE id = ?", user.getId());

        Optional<User> found = userRepository.findByDiscordId("discord-deleted-holder");

        assertTrue(found.isPresent());
        assertEquals(user.getId(), found.get().getId());
        assertTrue(found.get().isDeleteFlag());
    }

    // findByIdForUpdateはfindByIdAndDeleteFlagFalseForUpdateと異なりdelete_flag条件を
    // 付けないため、論理削除済みのUserもlock付きで取得できる(discord_id解除処理の対象に
    // するため)ことを確認する。
    @Test
    @Transactional
    void findByIdForUpdate_論理削除されたUserもlock付きで取得できる() {
        Long characterId = anyExistingCharacterId();
        User user = createUser(characterId);
        jdbcTemplate.update("UPDATE t_users SET delete_flag = true WHERE id = ?", user.getId());

        Optional<User> found = userRepository.findByIdForUpdate(user.getId());

        assertTrue(found.isPresent());
        assertEquals(user.getId(), found.get().getId());
    }

    private Long anyExistingCharacterId() {
        List<Character> characters = characterRepository.findAll();
        assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないため統合テストをスキップする");
        return characters.get(0).getId();
    }

    private User createUser(Long characterId) {
        String email = "user-repo-test-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest request = new UserCreateRequest(
                email,
                "password123",
                "User Repo Test User",
                List.of(new UserCharacterRequest(characterId, "MASTER", 1600)),
                null,
                null,
                null
        );

        UserCreateResponse response = userService.createUser(request);
        createdUserIds.add(response.id());

        return userRepository.findById(response.id()).orElseThrow();
    }
}
