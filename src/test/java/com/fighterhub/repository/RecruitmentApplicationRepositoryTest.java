package com.fighterhub.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.LocalDateTime;
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

import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.service.UserService;

// RecruitmentApplicationRepositoryの導出クエリと、statusのEnumType.STRING保存が
// 実際のPostgreSQL上でも期待どおり機能することを確認する統合テスト。
// 既存のTeamMemberRepositoryTest等と同じ、ローカルPostgreSQLへ直接接続する
// @SpringBootTest方式・手動cleanup方式をそのまま踏襲している。
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=" + RecruitmentApplicationRepositoryTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class RecruitmentApplicationRepositoryTest {

    // TEST_ONLY_JWT_SECRET: このテストのApplicationContext起動のためだけのダミー値
    // (Base64, 32byte, 全byteゼロ)。本番のJWT_SECRET環境変数とは無関係。
    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private RecruitmentApplicationRepository recruitmentApplicationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdApplicationIds = new ArrayList<>();
    private final List<Long> createdTeamIds = new ArrayList<>();
    private final List<Long> createdTournamentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 既存のローカルfighter_hub DBを使い回すため、作成したテストデータは
        // FK依存の逆順(RecruitmentApplication→Team→Tournament→User)で必ず削除する。
        createdApplicationIds.forEach(recruitmentApplicationRepository::deleteById);
        createdTeamIds.forEach(teamRepository::deleteById);
        createdTournamentIds.forEach(tournamentRepository::deleteById);
        createdUserIds.forEach(userRepository::deleteById);
    }

    @Test
    void existsByTeam_IdAndUser_IdAndStatus_teamIdとuserIdとPENDINGが一致する場合はtrueを返す() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User applicant = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createRecruitmentApplication(team, applicant, "よろしくお願いします");

        assertTrue(recruitmentApplicationRepository.existsByTeam_IdAndUser_IdAndStatus(
                team.getId(), applicant.getId(), RecruitmentApplicationStatus.PENDING));
    }

    @Test
    void existsByTeam_IdAndUser_IdAndStatus_別Teamまたは別Userの場合はfalseを返す() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User applicant = createUser(characterId);
        User otherUser = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        Team otherTeam = createTeam(tournament, otherUser);
        createRecruitmentApplication(team, applicant, "よろしくお願いします");

        assertFalse(recruitmentApplicationRepository.existsByTeam_IdAndUser_IdAndStatus(
                otherTeam.getId(), applicant.getId(), RecruitmentApplicationStatus.PENDING));
        assertFalse(recruitmentApplicationRepository.existsByTeam_IdAndUser_IdAndStatus(
                team.getId(), otherUser.getId(), RecruitmentApplicationStatus.PENDING));
    }

    @Test
    void existsByTeam_IdAndUser_IdAndStatus_APPROVEDまたはREJECTEDの場合はPENDING確認にヒットしない() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User applicant = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        RecruitmentApplication approvedApplication =
                createRecruitmentApplication(team, applicant, "よろしくお願いします");

        jdbcTemplate.update(
                "UPDATE t_recruitment_application SET status = 'APPROVED' WHERE id = ?",
                approvedApplication.getId());

        assertFalse(recruitmentApplicationRepository.existsByTeam_IdAndUser_IdAndStatus(
                team.getId(), applicant.getId(), RecruitmentApplicationStatus.PENDING));

        jdbcTemplate.update(
                "UPDATE t_recruitment_application SET status = 'REJECTED' WHERE id = ?",
                approvedApplication.getId());

        assertFalse(recruitmentApplicationRepository.existsByTeam_IdAndUser_IdAndStatus(
                team.getId(), applicant.getId(), RecruitmentApplicationStatus.PENDING));
    }

    @Test
    void statusがEnumTypeSTRINGとしてDBに保存され取得時にもEnumへ復元される() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User applicant = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        RecruitmentApplication application = createRecruitmentApplication(team, applicant, "よろしくお願いします");

        String storedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM t_recruitment_application WHERE id = ?",
                String.class,
                application.getId());
        assertEquals("PENDING", storedStatus);

        RecruitmentApplication found = recruitmentApplicationRepository.findById(application.getId()).orElseThrow();
        assertEquals(RecruitmentApplicationStatus.PENDING, found.getStatus());
    }

    @Test
    void messageがnullでも保存できる() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User applicant = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        RecruitmentApplication application = createRecruitmentApplication(team, applicant, null);

        Optional<RecruitmentApplication> found = recruitmentApplicationRepository.findById(application.getId());

        assertTrue(found.isPresent());
        assertNull(found.get().getMessage());
    }

    private Long anyExistingCharacterId() {
        List<Character> characters = characterRepository.findAll();
        assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないため統合テストをスキップする");
        return characters.get(0).getId();
    }

    private User createUser(Long characterId) {
        String email = "recruitment-application-repo-test-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest request = new UserCreateRequest(
                email,
                "password123",
                "Recruitment Application Repo Test User",
                List.of(new UserCharacterRequest(characterId, "MASTER", 1600)),
                null,
                null,
                null
        );

        UserCreateResponse response = userService.createUser(request);
        createdUserIds.add(response.id());

        return userRepository.findById(response.id()).orElseThrow();
    }

    private Tournament createTournament() {
        Tournament tournament = tournamentRepository.save(Tournament.create(
                "Test Cup " + UUID.randomUUID(),
                3,
                LocalDateTime.now(),
                24,
                "OPEN"
        ));
        createdTournamentIds.add(tournament.getId());

        return tournament;
    }

    private Team createTeam(Tournament tournament, User owner) {
        Team team = teamRepository.save(Team.create(
                tournament, owner, "Test Team " + UUID.randomUUID(), null, null, null));
        createdTeamIds.add(team.getId());

        return team;
    }

    private RecruitmentApplication createRecruitmentApplication(Team team, User user, String message) {
        RecruitmentApplication application =
                recruitmentApplicationRepository.save(RecruitmentApplication.create(team, user, message));
        createdApplicationIds.add(application.getId());

        return application;
    }
}
