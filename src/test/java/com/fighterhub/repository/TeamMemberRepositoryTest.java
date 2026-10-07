package com.fighterhub.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    void countByTeam_Id_指定Teamの人数を取得しownerも含む別Teamは含めない() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User member = createUser(characterId);
        User otherOwner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        Team otherTeam = createTeam(tournament, otherOwner);
        createTeamMember(team, owner);
        createTeamMember(team, member);
        createTeamMember(otherTeam, otherOwner);

        assertEquals(2L, teamMemberRepository.countByTeam_Id(team.getId()));
    }

    @Test
    void countByTeam_Id_メンバーが0人の場合は0を返す() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);

        assertEquals(0L, teamMemberRepository.countByTeam_Id(team.getId()));
    }

    // 「現在有効なメンバー数」の定義統一(定員判定・公開メンバー表示・available検索で同じ定義を使う)の
    // 確認。TeamMemberが3件存在しても1人がdeleteFlag=trueなら有効人数は2になる。
    // countByTeam_Id(全件カウント)との違いも併せて確認する。
    @Test
    void countByTeam_IdAndUser_DeleteFlagFalse_deleteFlagtrueのUserは有効人数に含めない() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User activeMember = createUser(characterId);
        User deletedMember = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);
        createTeamMember(team, activeMember);
        createTeamMember(team, deletedMember);

        jdbcTemplate.update("UPDATE t_users SET delete_flag = true WHERE id = ?", deletedMember.getId());

        assertEquals(3L, teamMemberRepository.countByTeam_Id(team.getId()));
        assertEquals(2L, teamMemberRepository.countByTeam_IdAndUser_DeleteFlagFalse(team.getId()));
    }

    @Test
    void countByTeam_IdAndUser_DeleteFlagFalse_メンバーが0人の場合は0を返す() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);

        assertEquals(0L, teamMemberRepository.countByTeam_IdAndUser_DeleteFlagFalse(team.getId()));
    }

    // チーム検索のavailable判定用(N+1回避)。複数Teamの有効メンバー数を1回のクエリで
    // まとめて取得でき、deleteFlag=trueのUserは含めず、有効なTeamMemberが0人のTeamは
    // GROUP BYのため結果に含まれないことを確認する(呼び出し側で0人として扱う前提)。
    @Test
    void countActiveMembersByTeamIds_複数TeamをまとめてdeleteFlagを考慮して集計し0人のTeamは結果に含まれない() {
        Long characterId = anyExistingCharacterId();
        User owner1 = createUser(characterId);
        User owner2 = createUser(characterId);
        User owner3 = createUser(characterId);
        User activeMember = createUser(characterId);
        User deletedMember = createUser(characterId);
        Tournament tournament = createTournament();
        Team teamWithTwoActiveMembers = createTeam(tournament, owner1);
        Team teamWithOneDeletedMember = createTeam(tournament, owner2);
        Team teamWithNoMembers = createTeam(tournament, owner3);
        createTeamMember(teamWithTwoActiveMembers, owner1);
        createTeamMember(teamWithTwoActiveMembers, activeMember);
        createTeamMember(teamWithOneDeletedMember, deletedMember);

        jdbcTemplate.update("UPDATE t_users SET delete_flag = true WHERE id = ?", deletedMember.getId());

        List<TeamActiveMemberCount> results = teamMemberRepository.countActiveMembersByTeamIds(List.of(
                teamWithTwoActiveMembers.getId(), teamWithOneDeletedMember.getId(), teamWithNoMembers.getId()));

        Map<Long, Long> countByTeamId = results.stream()
                .collect(Collectors.toMap(TeamActiveMemberCount::getTeamId, TeamActiveMemberCount::getActiveMemberCount));

        assertEquals(2L, countByTeamId.get(teamWithTwoActiveMembers.getId()));
        // deleteFlag=trueのUserしかいないTeamは有効メンバー0人のため、GROUP BYの結果に現れない。
        assertFalse(countByTeamId.containsKey(teamWithOneDeletedMember.getId()));
        assertFalse(countByTeamId.containsKey(teamWithNoMembers.getId()));
    }

    @Test
    void findByTeam_IdAndUser_Id_teamIdとuserIdが一致する場合TeamMemberを取得できる() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);

        Optional<TeamMember> found = teamMemberRepository.findByTeam_IdAndUser_Id(team.getId(), owner.getId());

        assertTrue(found.isPresent());
        assertEquals(team.getId(), found.get().getTeam().getId());
        assertEquals(owner.getId(), found.get().getUser().getId());
    }

    @Test
    void findByTeam_IdAndUser_Id_別Teamまたは別Userを指定した場合は取得できない() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User otherUser = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        Team otherTeam = createTeam(tournament, otherUser);
        createTeamMember(team, owner);
        createTeamMember(otherTeam, otherUser);

        assertTrue(teamMemberRepository.findByTeam_IdAndUser_Id(otherTeam.getId(), owner.getId()).isEmpty());
        assertTrue(teamMemberRepository.findByTeam_IdAndUser_Id(team.getId(), otherUser.getId()).isEmpty());
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

    @Test
    void findActiveTeamMembersByTeamId_指定Teamのメンバーを取得できる() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);

        List<Long> userIds = teamMemberRepository.findActiveTeamMembersByTeamId(team.getId())
                .stream().map(tm -> tm.getUser().getId()).toList();

        assertTrue(userIds.contains(owner.getId()));
    }

    @Test
    void findActiveTeamMembersByTeamId_別Teamのメンバーは含まれない() {
        Long characterId = anyExistingCharacterId();
        User owner1 = createUser(characterId);
        User owner2 = createUser(characterId);
        Tournament tournament = createTournament();
        Team team1 = createTeam(tournament, owner1);
        Team team2 = createTeam(tournament, owner2);
        createTeamMember(team1, owner1);
        createTeamMember(team2, owner2);

        List<Long> userIds = teamMemberRepository.findActiveTeamMembersByTeamId(team1.getId())
                .stream().map(tm -> tm.getUser().getId()).toList();

        assertTrue(userIds.contains(owner1.getId()));
        assertFalse(userIds.contains(owner2.getId()));
    }

    @Test
    void findActiveTeamMembersByTeamId_deleteFlagtrueのUserは含まれない() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User otherMember = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);
        createTeamMember(team, otherMember);

        jdbcTemplate.update("UPDATE t_users SET delete_flag = true WHERE id = ?", otherMember.getId());

        List<Long> userIds = teamMemberRepository.findActiveTeamMembersByTeamId(team.getId())
                .stream().map(tm -> tm.getUser().getId()).toList();

        assertTrue(userIds.contains(owner.getId()));
        assertFalse(userIds.contains(otherMember.getId()));
    }

    @Test
    void findActiveTeamMembersByTeamId_joinedAt昇順で返る() {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User otherMember = createUser(characterId);
        Tournament tournament = createTournament();
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);
        createTeamMember(team, otherMember);

        // @CreationTimestampはEntity APIから変更できないため、順序を決定的にするために
        // JDBCで直接joined_atを設定する(既存のsoft-delete手法と同じ、JDBCによる直接更新)。
        jdbcTemplate.update("UPDATE t_team_members SET joined_at = ? WHERE team_id = ? AND user_id = ?",
                LocalDateTime.now().minusHours(2), team.getId(), owner.getId());
        jdbcTemplate.update("UPDATE t_team_members SET joined_at = ? WHERE team_id = ? AND user_id = ?",
                LocalDateTime.now().minusHours(1), team.getId(), otherMember.getId());

        List<Long> userIds = teamMemberRepository.findActiveTeamMembersByTeamId(team.getId())
                .stream().map(tm -> tm.getUser().getId()).toList();

        assertEquals(List.of(owner.getId(), otherMember.getId()), userIds);
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
                LocalDateTime.now().minusDays(1),
                24
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
