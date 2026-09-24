package com.fighterhub.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.service.UserService;

// TeamMemberRepositoryの導出クエリと、(team_id, user_id)のUNIQUE制約が
// 実際のPostgreSQL上でも期待どおり機能することを確認する統合テスト。
// 既存のUserServicePersistenceTest等と同じ、ローカルPostgreSQLへ直接接続する
// @SpringBootTest方式・手動cleanup方式をそのまま踏襲している。
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=" + TeamMemberRepositoryTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class TeamMemberRepositoryTest {

    // TEST_ONLY_JWT_SECRET: このテストのApplicationContext起動のためだけのダミー値
    // (Base64, 32byte, 全byteゼロ)。本番のJWT_SECRET環境変数とは無関係。
    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private TeamMemberRepository teamMemberRepository;

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

    private final List<Long> createdTeamMemberIds = new ArrayList<>();
    private final List<Long> createdTeamIds = new ArrayList<>();
    private final List<Long> createdTournamentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 既存のローカルfighter_hub DBを使い回すため、作成したテストデータは
        // FK依存の逆順(TeamMember→Team→Tournament→User)で必ず削除する。
        createdTeamMemberIds.forEach(teamMemberRepository::deleteById);
        createdTeamIds.forEach(teamRepository::deleteById);
        createdTournamentIds.forEach(tournamentRepository::deleteById);
        createdUserIds.forEach(userRepository::deleteById);
    }

    @Test
    void existsByTeam_IdAndUser_Id_同じTeamへ所属している場合はtrueそれ以外はfalseを返す() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User otherUser = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);

        assertTrue(teamMemberRepository.existsByTeam_IdAndUser_Id(team.getId(), owner.getId()));
        assertFalse(teamMemberRepository.existsByTeam_IdAndUser_Id(team.getId(), otherUser.getId()));
    }

    @Test
    void existsByUser_IdAndTeam_Tournament_Id_同一Tournament内のTeamに所属していればtrue別Tournamentならfalseを返す() {
        Long characterId = anyExistingCharacterId();
        User user = createUser(characterId);
        Tournament tournamentA = createTournament();
        Tournament tournamentB = createTournament();
        Team teamInTournamentA = createTeam(tournamentA, user);
        createTeamMember(teamInTournamentA, user);

        assertTrue(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(
                user.getId(), tournamentA.getId()));
        assertFalse(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(
                user.getId(), tournamentB.getId()));
    }

    @Test
    void 同じteam_idとuser_idの組み合わせを2件保存しようとするとUNIQUE制約違反になる() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);

        TeamMember duplicate = TeamMember.create(team, owner);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> teamMemberRepository.save(duplicate));
    }

    @Test
    void findActiveTeamsByMemberUserId_ownerであるTeamを取得できる() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);

        List<Long> foundIds = teamMemberRepository.findActiveTeamMembershipsByUserId(owner.getId())
                .stream().map(tm -> tm.getTeam().getId()).toList();

        assertTrue(foundIds.contains(team.getId()));
    }

    @Test
    void findActiveTeamsByMemberUserId_ownerではないTeamMemberとして所属するTeamも取得できる() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User nonOwnerMember = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        // ownerだけでなく、owner以外のTeamMemberも登録する。
        createTeamMember(team, owner);
        createTeamMember(team, nonOwnerMember);

        List<Long> foundIds = teamMemberRepository.findActiveTeamMembershipsByUserId(nonOwnerMember.getId())
                .stream().map(tm -> tm.getTeam().getId()).toList();

        // nonOwnerMemberはこのTeamのownerではないが、TeamMemberとして所属しているため
        // 取得できることを確認する(ownerId基準の検索になっていないことの確認)。
        assertTrue(foundIds.contains(team.getId()));
        assertNotEquals(team.getOwner().getId(), nonOwnerMember.getId());
    }

    @Test
    void findActiveTeamsByMemberUserId_TeamdeleteFlagtrueの所属Teamは除外する() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);

        jdbcTemplate.update("UPDATE t_teams SET delete_flag = true WHERE id = ?", team.getId());

        List<Long> foundIds = teamMemberRepository.findActiveTeamMembershipsByUserId(owner.getId())
                .stream().map(tm -> tm.getTeam().getId()).toList();

        assertFalse(foundIds.contains(team.getId()));
    }

    @Test
    void findActiveTeamsByMemberUserId_TournamentdeleteFlagtrueの所属Teamは除外する() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);

        jdbcTemplate.update("UPDATE t_tournaments SET delete_flag = true WHERE id = ?", tournament.getId());

        List<Long> foundIds = teamMemberRepository.findActiveTeamMembershipsByUserId(owner.getId())
                .stream().map(tm -> tm.getTeam().getId()).toList();

        assertFalse(foundIds.contains(team.getId()));
    }

    @Test
    void findActiveTeamsByMemberUserId_ownerdeleteFlagtrueの所属Teamは除外する() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);

        jdbcTemplate.update("UPDATE t_users SET delete_flag = true WHERE id = ?", owner.getId());

        List<Long> foundIds = teamMemberRepository.findActiveTeamMembershipsByUserId(owner.getId())
                .stream().map(tm -> tm.getTeam().getId()).toList();

        assertFalse(foundIds.contains(team.getId()));
    }

    private Long anyExistingCharacterId() {
        List<Character> characters = characterRepository.findAll();
        assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないため統合テストをスキップする");
        return characters.get(0).getId();
    }

    private User createUser(Long characterId) {
        String email = "team-member-repo-test-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest request = new UserCreateRequest(
                email,
                "password123",
                "Team Member Repo Test User",
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

    private void createTeamMember(Team team, User user) {
        TeamMember teamMember = teamMemberRepository.save(TeamMember.create(team, user));
        createdTeamMemberIds.add(teamMember.getId());
    }
}
