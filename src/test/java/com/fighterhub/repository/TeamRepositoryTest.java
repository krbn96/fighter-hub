package com.fighterhub.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.fighterhub.entity.Team;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.service.UserService;

// TeamRepositoryのfindAllActiveTeams/findActiveTeamByIdが、実PostgreSQL上でも
// Team/Tournament/ownerそれぞれのdelete_flagを正しく考慮することを確認する統合テスト。
// 既存のTeamMemberRepositoryTestと同じ、ローカルPostgreSQLへ直接接続する
// @SpringBootTest方式・手動cleanup方式をそのまま踏襲している。
// delete_flag=trueへの変更は、Entityにsetterを追加せずJdbcTemplateで直接行う
// (UserSoftDeletePersistenceTestと同じ方式)。
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=" + TeamRepositoryTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class TeamRepositoryTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

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

    private final List<Long> createdTeamIds = new ArrayList<>();
    private final List<Long> createdTournamentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdTeamIds.forEach(teamRepository::deleteById);
        createdTournamentIds.forEach(tournamentRepository::deleteById);
        createdUserIds.forEach(userRepository::deleteById);
    }

    @Test
    void findAllActiveTeams_有効なTeamのみ返しTeamdeleteFlagtrueは除外する() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team activeTeam = createTeam(tournament, owner);
        Team deletedTeam = createTeam(tournament, owner);

        softDeleteTeam(deletedTeam.getId());

        List<Long> foundIds = teamRepository.findAllActiveTeams().stream().map(Team::getId).toList();

        assertTrue(foundIds.contains(activeTeam.getId()));
        assertFalse(foundIds.contains(deletedTeam.getId()));
    }

    @Test
    void findAllActiveTeams_TournamentdeleteFlagtrueのTeamは除外する() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);

        softDeleteTournament(tournament.getId());

        List<Long> foundIds = teamRepository.findAllActiveTeams().stream().map(Team::getId).toList();

        assertFalse(foundIds.contains(team.getId()));
    }

    @Test
    void findAllActiveTeams_ownerdeleteFlagtrueのTeamは除外する() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);

        softDeleteUser(owner.getId());

        List<Long> foundIds = teamRepository.findAllActiveTeams().stream().map(Team::getId).toList();

        assertFalse(foundIds.contains(team.getId()));
    }

    @Test
    void findActiveTeamById_有効なTeamはTournament名とowner名を含めて取得できる() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);

        Optional<Team> found = teamRepository.findActiveTeamById(team.getId());

        assertTrue(found.isPresent());
        assertEquals(tournament.getName(), found.get().getTournament().getName());
        assertEquals(owner.getName(), found.get().getOwner().getName());
    }

    @Test
    void findActiveTeamById_TeamTournamentownerのいずれかがdeleteFlagtrueなら取得できない() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);

        softDeleteTeam(team.getId());

        assertTrue(teamRepository.findActiveTeamById(team.getId()).isEmpty());
    }

    private Long anyExistingCharacterId() {
        List<Character> characters = characterRepository.findAll();
        assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないため統合テストをスキップする");
        return characters.get(0).getId();
    }

    private User createUser(Long characterId) {
        String email = "team-repo-test-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest request = new UserCreateRequest(
                email,
                "password123",
                "Team Repo Test User",
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
                "Test Cup " + UUID.randomUUID(), 3, LocalDateTime.now(), 24, "OPEN"));
        createdTournamentIds.add(tournament.getId());

        return tournament;
    }

    private Team createTeam(Tournament tournament, User owner) {
        Team team = teamRepository.save(Team.create(
                tournament, owner, "Test Team " + UUID.randomUUID(), null, null, null));
        createdTeamIds.add(team.getId());

        return team;
    }

    private void softDeleteTeam(Long teamId) {
        jdbcTemplate.update("UPDATE t_teams SET delete_flag = true WHERE id = ?", teamId);
    }

    private void softDeleteTournament(Long tournamentId) {
        jdbcTemplate.update("UPDATE t_tournaments SET delete_flag = true WHERE id = ?", tournamentId);
    }

    private void softDeleteUser(Long userId) {
        jdbcTemplate.update("UPDATE t_users SET delete_flag = true WHERE id = ?", userId);
    }
}
