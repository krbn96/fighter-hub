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
import org.springframework.transaction.annotation.Transactional;

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
    void existsByUser_IdAndTeam_Tournament_IdAndStatus_同一Userと同一TournamentとPENDINGが一致する場合はtrueを返す() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User applicant = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createRecruitmentApplication(team, applicant, "よろしくお願いします");

        assertTrue(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                applicant.getId(), tournament.getId(), RecruitmentApplicationStatus.PENDING));
    }

    @Test
    void existsByUser_IdAndTeam_Tournament_IdAndStatus_同一Userでも別TeamのPENDINGなら同じTournamentならtrueを返す() {
        Long characterId = anyExistingCharacterId();
        User otherOwner = createUser(characterId);
        User applicant = createUser(characterId);
        Tournament tournament = createTournament();
        Team otherTeamSameTournament = createTeam(tournament, otherOwner);
        // Team単位ではなくTournament単位で判定するため、別Teamへの申請でもtrueになることを確認する。
        createRecruitmentApplication(otherTeamSameTournament, applicant, "よろしくお願いします");

        assertTrue(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                applicant.getId(), tournament.getId(), RecruitmentApplicationStatus.PENDING));
    }

    @Test
    void existsByUser_IdAndTeam_Tournament_IdAndStatus_別Tournamentまたは別Userまたは別statusの場合はfalseを返す() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User applicant = createUser(characterId);
        User otherUser = createUser(characterId);
        Tournament tournament = createTournament();
        Tournament otherTournament = createTournament();
        Team team = createTeam(tournament, owner);
        RecruitmentApplication application = createRecruitmentApplication(team, applicant, "よろしくお願いします");

        // 別Tournament
        assertFalse(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                applicant.getId(), otherTournament.getId(), RecruitmentApplicationStatus.PENDING));
        // 別User
        assertFalse(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                otherUser.getId(), tournament.getId(), RecruitmentApplicationStatus.PENDING));

        // REJECTEDのみの場合はPENDING確認にヒットしない
        jdbcTemplate.update(
                "UPDATE t_recruitment_application SET status = 'REJECTED' WHERE id = ?",
                application.getId());
        assertFalse(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                applicant.getId(), tournament.getId(), RecruitmentApplicationStatus.PENDING));
    }

    // PESSIMISTIC_WRITEロックの取得にはアクティブなTransactionが必須のため、
    // 本番でのService呼び出し(@Transactional内)を模して、テストメソッド自体を@Transactionalにする。
    @Test
    @Transactional
    void findByIdForUpdate_lock付きでApplicationを取得できる() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User applicant = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        RecruitmentApplication application = createRecruitmentApplication(team, applicant, "よろしくお願いします");

        Optional<RecruitmentApplication> found =
                recruitmentApplicationRepository.findByIdForUpdate(application.getId());

        assertTrue(found.isPresent());
        assertEquals(application.getId(), found.get().getId());
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

    @Test
    void findByTeamIdOrderByCreatedAtDesc_指定Teamの全status申請をcreatedAt降順で取得できる() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User otherOwner = createUser(characterId);
        User applicant = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        Team otherTeam = createTeam(tournament, otherOwner);

        RecruitmentApplication oldest = createRecruitmentApplication(team, applicant, "1件目");
        RecruitmentApplication middle = createRecruitmentApplication(team, applicant, "2件目");
        RecruitmentApplication newest = createRecruitmentApplication(team, applicant, "3件目");
        // 別Teamへの申請は結果に含まれないことの確認用。
        createRecruitmentApplication(otherTeam, applicant, "別Teamへの申請");

        updateCreatedAt(oldest.getId(), LocalDateTime.now().minusHours(3));
        updateCreatedAt(middle.getId(), LocalDateTime.now().minusHours(2));
        updateCreatedAt(newest.getId(), LocalDateTime.now().minusHours(1));
        updateStatus(middle.getId(), RecruitmentApplicationStatus.APPROVED);
        updateStatus(newest.getId(), RecruitmentApplicationStatus.REJECTED);

        List<RecruitmentApplication> found =
                recruitmentApplicationRepository.findByTeamIdOrderByCreatedAtDesc(team.getId());

        assertEquals(
                List.of(newest.getId(), middle.getId(), oldest.getId()),
                found.stream().map(RecruitmentApplication::getId).toList());
        assertEquals(
                List.of(
                        RecruitmentApplicationStatus.REJECTED,
                        RecruitmentApplicationStatus.APPROVED,
                        RecruitmentApplicationStatus.PENDING),
                found.stream().map(RecruitmentApplication::getStatus).toList());
    }

    @Test
    void findByTeamIdOrderByCreatedAtDesc_申請が0件の場合は空Listを返す() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);

        List<RecruitmentApplication> found =
                recruitmentApplicationRepository.findByTeamIdOrderByCreatedAtDesc(team.getId());

        assertTrue(found.isEmpty());
    }

    @Test
    void findByUser_IdOrderByCreatedAtDesc_指定Userの複数Teamへの全status申請をcreatedAt降順で取得できる() {
        Long characterId = anyExistingCharacterId();
        User owner1 = createUser(characterId);
        User owner2 = createUser(characterId);
        User applicant = createUser(characterId);
        User otherUser = createUser(characterId);
        Tournament tournament = createTournament();
        Team team1 = createTeam(tournament, owner1);
        Team team2 = createTeam(tournament, owner2);

        RecruitmentApplication oldest = createRecruitmentApplication(team1, applicant, "1件目");
        RecruitmentApplication middle = createRecruitmentApplication(team2, applicant, "2件目");
        RecruitmentApplication newest = createRecruitmentApplication(team1, applicant, "3件目");
        // 別Userの申請は結果に含まれないことの確認用。
        createRecruitmentApplication(team1, otherUser, "別Userの申請");

        updateCreatedAt(oldest.getId(), LocalDateTime.now().minusHours(3));
        updateCreatedAt(middle.getId(), LocalDateTime.now().minusHours(2));
        updateCreatedAt(newest.getId(), LocalDateTime.now().minusHours(1));
        updateStatus(middle.getId(), RecruitmentApplicationStatus.APPROVED);
        updateStatus(newest.getId(), RecruitmentApplicationStatus.REJECTED);

        List<RecruitmentApplication> found =
                recruitmentApplicationRepository.findByUser_IdOrderByCreatedAtDesc(applicant.getId());

        assertEquals(
                List.of(newest.getId(), middle.getId(), oldest.getId()),
                found.stream().map(RecruitmentApplication::getId).toList());
        assertEquals(
                List.of(team1.getId(), team2.getId(), team1.getId()),
                found.stream().map(a -> a.getTeam().getId()).toList());
    }

    @Test
    void findByUser_IdOrderByCreatedAtDesc_申請が0件の場合は空Listを返す() {
        Long characterId = anyExistingCharacterId();
        User applicant = createUser(characterId);

        List<RecruitmentApplication> found =
                recruitmentApplicationRepository.findByUser_IdOrderByCreatedAtDesc(applicant.getId());

        assertTrue(found.isEmpty());
    }

    private void updateCreatedAt(Long applicationId, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE t_recruitment_application SET created_at = ? WHERE id = ?",
                createdAt, applicationId);
    }

    private void updateStatus(Long applicationId, RecruitmentApplicationStatus status) {
        jdbcTemplate.update(
                "UPDATE t_recruitment_application SET status = ? WHERE id = ?",
                status.name(), applicationId);
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
                LocalDateTime.now().minusDays(1),
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
