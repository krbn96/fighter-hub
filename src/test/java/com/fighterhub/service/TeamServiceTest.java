package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;

import com.fighterhub.dto.TeamCreateRequest;
import com.fighterhub.dto.TeamCreateResponse;
import com.fighterhub.dto.TeamMemberResponse;
import com.fighterhub.dto.TeamResponse;
import com.fighterhub.dto.TeamUpdateRequest;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.exception.CannotRemoveTeamOwnerException;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
import com.fighterhub.exception.InvalidRequestException;
import com.fighterhub.exception.NotTeamOwnerException;
import com.fighterhub.exception.RecruitmentClosedException;
import com.fighterhub.exception.TeamMemberNotFoundException;
import com.fighterhub.exception.TeamNotFoundException;
import com.fighterhub.exception.TournamentNotFoundException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.repository.TeamActiveMemberCount;
import com.fighterhub.repository.TeamMemberRepository;
import com.fighterhub.repository.TeamRepository;
import com.fighterhub.repository.TournamentRepository;
import com.fighterhub.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private RecruitmentApplicationService recruitmentApplicationService;

    @Mock
    private Clock clock;

    @InjectMocks
    private TeamService teamService;

    // 募集締切境界値のテスト以外はClockの具体的な値を気にしないため、
    // LocalDateTime.now(clock)がNPEにならないよう最低限のstubをlenientで用意する
    // (isRecruitmentOpen自体はmockTournament()側でany()マッチにより常にtrueを返すようにしている)。
    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(Instant.now());
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private static TeamCreateRequest requestWithCharacters(List<Long> characterRequirements) {
        return new TeamCreateRequest(
                10L,
                "Team Ryu",
                "MASTER",
                characterRequirements,
                "誰でも歓迎です"
        );
    }

    private static TeamCreateRequest requestWithRankRequirement(String rankRequirement) {
        return new TeamCreateRequest(
                10L,
                "Team Ryu",
                rankRequirement,
                null,
                "誰でも歓迎です"
        );
    }

    private User mockOwner() {
        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(1L);
        lenient().when(owner.getName()).thenReturn("Owner User");
        return owner;
    }

    private Tournament mockTournament() {
        Tournament tournament = mock(Tournament.class);
        lenient().when(tournament.getId()).thenReturn(10L);
        lenient().when(tournament.getName()).thenReturn("Test Cup");
        // 募集締切の境界値自体を検証するテスト以外は、常に募集中(締切前)として扱う。
        lenient().when(tournament.isRecruitmentOpen(any())).thenReturn(true);
        return tournament;
    }

    private Team mockSavedTeam(Tournament tournament, User owner) {
        Team savedTeam = mock(Team.class);
        lenient().when(savedTeam.getTournament()).thenReturn(tournament);
        lenient().when(savedTeam.getOwner()).thenReturn(owner);
        return savedTeam;
    }

    private Team mockFullTeam(Long id, Tournament tournament, User owner) {
        Team team = mock(Team.class);
        lenient().when(team.getId()).thenReturn(id);
        lenient().when(team.getTournament()).thenReturn(tournament);
        lenient().when(team.getOwner()).thenReturn(owner);
        lenient().when(team.getName()).thenReturn("Team Ryu");
        lenient().when(team.getRankRequirement()).thenReturn("MASTER");
        lenient().when(team.getCharacterRequirements()).thenReturn(List.of(1L, 2L));
        lenient().when(team.getRecruitmentMessage()).thenReturn("誰でも歓迎です");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        lenient().when(team.getCreatedAt()).thenReturn(now);
        lenient().when(team.getUpdatedAt()).thenReturn(now);
        return team;
    }

    @Test
    void createTeam_正常系の場合_TeamとownerのTeamMemberが保存されResponseを返す() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(false);
        when(characterRepository.existsById(1L)).thenReturn(true);
        when(characterRepository.existsById(2L)).thenReturn(true);

        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        Team savedTeam = mock(Team.class);
        when(savedTeam.getId()).thenReturn(100L);
        when(savedTeam.getTournament()).thenReturn(tournament);
        when(savedTeam.getOwner()).thenReturn(owner);
        when(savedTeam.getName()).thenReturn("Team Ryu");
        when(savedTeam.getRankRequirement()).thenReturn("MASTER");
        when(savedTeam.getCharacterRequirements()).thenReturn(List.of(1L, 2L));
        when(savedTeam.getRecruitmentMessage()).thenReturn("誰でも歓迎です");
        when(savedTeam.getCreatedAt()).thenReturn(now);
        when(savedTeam.getUpdatedAt()).thenReturn(now);

        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        TeamCreateResponse response = teamService.createTeam(1L, requestWithCharacters(List.of(1L, 2L)));

        assertEquals(100L, response.id());
        assertEquals(10L, response.tournamentId());
        assertEquals(1L, response.ownerId());
        assertEquals("Team Ryu", response.name());
        assertEquals("MASTER", response.rankRequirement());
        assertEquals(List.of(1L, 2L), response.characterRequirements());
        assertEquals("誰でも歓迎です", response.recruitmentMessage());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());

        ArgumentCaptor<TeamMember> teamMemberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository, times(1)).save(teamMemberCaptor.capture());
        assertEquals(savedTeam, teamMemberCaptor.getValue().getTeam());
        assertEquals(owner, teamMemberCaptor.getValue().getUser());
    }

    // rankRequirementはnull(指定なし)を許可する。ランク定義はRank enumを再利用する。
    @Test
    void createTeam_rankRequirementがnullの場合_Teamを作成できる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(false);

        Team savedTeam = mockSavedTeam(tournament, owner);
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        TeamCreateResponse response = teamService.createTeam(1L, requestWithRankRequirement(null));

        assertNotNull(response);
        verify(teamMemberRepository, times(1)).save(any(TeamMember.class));
    }

    // rankRequirementがRank enum(8値)のいずれかであれば作成できる(境界: 最低値と最高値)。
    @Test
    void createTeam_rankRequirementがRankenumの値の場合_Teamを作成できる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(false);

        Team savedTeam = mockSavedTeam(tournament, owner);
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        assertNotNull(teamService.createTeam(1L, requestWithRankRequirement("ROOKIE")));
        assertNotNull(teamService.createTeam(1L, requestWithRankRequirement("MASTER")));
    }

    @Test
    void createTeam_rankRequirementが不正な値の場合_InvalidRequestExceptionを投げTeamは作成されない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(false);

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> teamService.createTeam(1L, requestWithRankRequirement("GRANDMASTER")));

        assertEquals("Invalid rankRequirement specified: GRANDMASTER", exception.getMessage());
        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    // 正式business rule: 募集締切(recruitmentDeadline)の境界値を固定Clockで検証する。
    // 締切ちょうど(now == recruitmentDeadline)も不可であることを含む3パターン。
    @Test
    void createTeam_募集締切前の場合_Teamを作成できる() {
        LocalDateTime deadline = LocalDateTime.of(2026, 10, 1, 23, 59, 59);
        LocalDateTime now = deadline.minusSeconds(1);
        TeamService serviceWithFixedClock = teamServiceWithFixedClock(now);

        User owner = mockOwner();
        Tournament tournament = mockTournament();
        when(tournament.isRecruitmentOpen(now)).thenReturn(true);

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(false);

        Team savedTeam = mockSavedTeam(tournament, owner);
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        TeamCreateResponse response = serviceWithFixedClock.createTeam(1L, requestWithCharacters(null));

        assertNotNull(response);
        verify(teamMemberRepository, times(1)).save(any(TeamMember.class));
    }

    @Test
    void createTeam_募集締切ちょうどの場合_RecruitmentClosedExceptionを投げTeamは作成されない() {
        LocalDateTime deadline = LocalDateTime.of(2026, 10, 1, 23, 59, 59);
        TeamService serviceWithFixedClock = teamServiceWithFixedClock(deadline);

        User owner = mockOwner();
        Tournament tournament = mockTournament();
        when(tournament.isRecruitmentOpen(deadline)).thenReturn(false);

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));

        assertThrows(
                RecruitmentClosedException.class,
                () -> serviceWithFixedClock.createTeam(1L, requestWithCharacters(null)));

        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void createTeam_募集締切後の場合_RecruitmentClosedExceptionを投げTeamは作成されない() {
        LocalDateTime deadline = LocalDateTime.of(2026, 10, 1, 23, 59, 59);
        LocalDateTime now = deadline.plusSeconds(1);
        TeamService serviceWithFixedClock = teamServiceWithFixedClock(now);

        User owner = mockOwner();
        Tournament tournament = mockTournament();
        when(tournament.isRecruitmentOpen(now)).thenReturn(false);

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));

        assertThrows(
                RecruitmentClosedException.class,
                () -> serviceWithFixedClock.createTeam(1L, requestWithCharacters(null)));

        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    // JwtProviderTestと同じ手法(Clock.fixed)で、募集締切の境界値を決定的に再現する。
    // 共有の@InjectMocksインスタンス(clockはMockitoモック)とは別に、このテストだけで使う
    // 固定Clock付きインスタンスを都度生成する。
    private TeamService teamServiceWithFixedClock(LocalDateTime now) {
        Clock fixedClock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new TeamService(
                teamRepository,
                teamMemberRepository,
                tournamentRepository,
                userRepository,
                characterRepository,
                recruitmentApplicationService,
                fixedClock);
    }

    // 正式business rule: 同一TournamentでUserがTeamMemberになった時点で、
    // そのUserが同一Tournament内の他TeamへのPENDING申請はREJECTEDへ遷移する。
    // Team作成によってownerは即座にTeamMemberになるため、createTeam成功時には必ず
    // 正しいuserId/tournamentIdでこの処理が呼び出されることを検証する。
    // 実際のREJECTEDへの遷移ロジック自体はRecruitmentApplicationServiceTest側で検証する。
    @Test
    void createTeam_成功時に同一TournamentのPENDING申請をREJECTEDへ遷移させる処理が呼び出される() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(false);

        Team savedTeam = mockSavedTeam(tournament, owner);
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        teamService.createTeam(1L, requestWithCharacters(null));

        verify(recruitmentApplicationService, times(1))
                .rejectOtherPendingApplicationsInTournament(1L, 10L);
    }

    @Test
    void createTeam_characterRequirementsがnullの場合_Character存在チェックをせず正常に作成する() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(false);

        Team savedTeam = mockSavedTeam(tournament, owner);
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        TeamCreateResponse response = teamService.createTeam(1L, requestWithCharacters(null));

        assertNotNull(response);
        verify(characterRepository, never()).existsById(any());
        verify(teamRepository, times(1)).save(any());
        verify(teamMemberRepository, times(1)).save(any());
    }

    @Test
    void createTeam_characterRequirementsが空リストの場合_Character存在チェックをせず正常に作成する() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(false);

        Team savedTeam = mockSavedTeam(tournament, owner);
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        TeamCreateResponse response = teamService.createTeam(1L, requestWithCharacters(List.of()));

        assertNotNull(response);
        verify(characterRepository, never()).existsById(any());
        verify(teamRepository, times(1)).save(any());
    }

    @Test
    void createTeam_Userが存在しない場合_UserNotFoundExceptionを投げる() {
        when(userRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> teamService.createTeam(999L, requestWithCharacters(null)));

        verify(tournamentRepository, never()).findByIdAndDeleteFlagFalse(any());
        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void createTeam_Tournamentが存在しない場合_TournamentNotFoundExceptionを投げる() {
        User owner = mockOwner();
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.empty());

        assertThrows(
                TournamentNotFoundException.class,
                () -> teamService.createTeam(1L, requestWithCharacters(null)));

        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void createTeam_同一Tournamentですでにチームに所属している場合_DuplicateTournamentMembershipExceptionを投げる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(true);

        assertThrows(
                DuplicateTournamentMembershipException.class,
                () -> teamService.createTeam(1L, requestWithCharacters(null)));

        verify(characterRepository, never()).existsById(any());
        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void createTeam_characterRequirementsに存在しないCharacterIdが含まれる場合_InvalidRequestExceptionを投げる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(owner));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(1L, 10L)).thenReturn(false);
        when(characterRepository.existsById(999L)).thenReturn(false);

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> teamService.createTeam(1L, requestWithCharacters(List.of(999L))));

        assertEquals("Character not found. character_id=999", exception.getMessage());
        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void findAllTeams_有効なTeamをTeamResponseへ変換して返す() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findAllActiveTeams()).thenReturn(List.of(team));

        List<TeamResponse> responses = teamService.findAllTeams();

        assertEquals(1, responses.size());
        TeamResponse response = responses.get(0);
        assertEquals(100L, response.id());
        assertEquals(10L, response.tournamentId());
        assertEquals("Test Cup", response.tournamentName());
        assertEquals(1L, response.ownerId());
        assertEquals("Owner User", response.ownerName());
        assertEquals("Team Ryu", response.name());
        assertEquals("MASTER", response.rankRequirement());
        assertEquals(List.of(1L, 2L), response.characterRequirements());
        assertEquals("誰でも歓迎です", response.recruitmentMessage());
    }

    @Test
    void findTeamsByTournament_正常系の場合_有効なTeamをTeamResponseへ変換して返す() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, null, null);

        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).id());
        assertEquals("Test Cup", responses.get(0).tournamentName());
        verify(teamRepository, never())
                .findActiveTeamsByTournamentIdAndNameContainingIgnoreCase(any(), any());
    }

    @Test
    void findTeamsByTournament_Tournamentが存在しない場合_TournamentNotFoundExceptionを投げる() {
        when(tournamentRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(
                TournamentNotFoundException.class,
                () -> teamService.findTeamsByTournament(999L, null, null, null, null));

        verify(teamRepository, never()).findActiveTeamsByTournamentId(any());
    }

    @Test
    void findTeamsByTournament_該当Teamが0件の場合は空Listを返す() {
        Tournament tournament = mockTournament();

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of());

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, null, null);

        assertTrue(responses.isEmpty());
    }

    @Test
    void findTeamsByTournament_nameが空白のみの場合は条件なしとして全件取得する() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, "   ", null, null, null);

        assertEquals(1, responses.size());
        verify(teamRepository, never())
                .findActiveTeamsByTournamentIdAndNameContainingIgnoreCase(any(), any());
    }

    @Test
    void findTeamsByTournament_name指定時はContainingIgnoreCase検索を前後空白を除いて使用する() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentIdAndNameContainingIgnoreCase(10L, "ryu"))
                .thenReturn(List.of(team));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, "  ryu  ", null, null, null);

        assertEquals(1, responses.size());
        verify(teamRepository, times(1))
                .findActiveTeamsByTournamentIdAndNameContainingIgnoreCase(10L, "ryu");
        verify(teamRepository, never()).findActiveTeamsByTournamentId(any());
    }

    @Test
    void findTeamsByTournament_characterId指定時は対象characterIdをcharacterRequirementsに含むTeamのみ返す() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team matchingTeam = mockFullTeam(100L, tournament, owner);
        lenient().when(matchingTeam.getCharacterRequirements()).thenReturn(List.of(1L, 2L));
        Team otherTeam = mockFullTeam(200L, tournament, owner);
        lenient().when(otherTeam.getCharacterRequirements()).thenReturn(List.of(3L));

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(matchingTeam, otherTeam));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, 2L, null, null);

        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).id());
    }

    @Test
    void findTeamsByTournament_characterId指定時characterRequirementsがnullのTeamは含まれない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team teamWithoutRequirement = mockFullTeam(100L, tournament, owner);
        lenient().when(teamWithoutRequirement.getCharacterRequirements()).thenReturn(null);

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(teamWithoutRequirement));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, 1L, null, null);

        assertTrue(responses.isEmpty());
    }

    @Test
    void findTeamsByTournament_characterIdに該当するTeamが存在しない場合は空Listを返す() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);
        lenient().when(team.getCharacterRequirements()).thenReturn(List.of(1L));

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));

        // 999Lは存在しないcharacterIdだが、特別扱いせず単に0件として返す(既存APIの方針と同様)。
        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, 999L, null, null);

        assertTrue(responses.isEmpty());
    }

    // rank境界: DIAMONDのプレイヤーはIRON(低)要求のチームも満たせる。
    @Test
    void findTeamsByTournament_rank指定時プレイヤーランクが要求ランクより高い場合は対象になる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);
        lenient().when(team.getRankRequirement()).thenReturn("IRON");

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, "DIAMOND", null);

        assertEquals(1, responses.size());
    }

    // rank境界: DIAMONDのプレイヤーはDIAMOND(同じ)要求のチームを満たせる(完全一致検索ではないが
    // 同一ランクは当然満たせる)。
    @Test
    void findTeamsByTournament_rank指定時プレイヤーランクが要求ランクと同じ場合は対象になる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);
        lenient().when(team.getRankRequirement()).thenReturn("DIAMOND");

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, "DIAMOND", null);

        assertEquals(1, responses.size());
    }

    // rank境界: DIAMONDのプレイヤーはMASTER(高)要求のチームを満たせない(対象外)。
    @Test
    void findTeamsByTournament_rank指定時プレイヤーランクが要求ランクより低い場合は対象外になる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);
        lenient().when(team.getRankRequirement()).thenReturn("MASTER");

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, "DIAMOND", null);

        assertTrue(responses.isEmpty());
    }

    // rankRequirement=null(指定なし)のTeamは、どのrankを指定しても常に対象になる。
    @Test
    void findTeamsByTournament_rank指定時rankRequirementがnullのTeamは常に対象になる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);
        lenient().when(team.getRankRequirement()).thenReturn(null);

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, "ROOKIE", null);

        assertEquals(1, responses.size());
    }

    @Test
    void findTeamsByTournament_rankが不正な値の場合_InvalidRequestExceptionを投げる() {
        Tournament tournament = mockTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));

        assertThrows(
                InvalidRequestException.class,
                () -> teamService.findTeamsByTournament(10L, null, null, "INVALID_RANK", null));
    }

    // availableの一括集計結果projection(TeamActiveMemberCount)のテスト用mock。
    private TeamActiveMemberCount mockActiveMemberCount(Long teamId, long activeMemberCount) {
        TeamActiveMemberCount result = mock(TeamActiveMemberCount.class);
        lenient().when(result.getTeamId()).thenReturn(teamId);
        lenient().when(result.getActiveMemberCount()).thenReturn(activeMemberCount);
        return result;
    }

    // available=trueでは、Teamごとのcount queryをループ呼び出しするのではなく、対象Team群を
    // 1回のcountActiveMembersByTeamIdsでまとめて取得した結果から判定する(N+1回避の確認)。
    @Test
    void findTeamsByTournament_available指定時は一括集計結果から定員未満のTeamのみ返す() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(2);
        Team availableTeam = mockFullTeam(100L, tournament, owner);
        Team fullTeam = mockFullTeam(200L, tournament, owner);

        // Mockitoの制約上、thenReturn()の引数(mockActiveMemberCountの呼び出し)は、外側のwhen()を
        // 呼び出す前に完全に評価しておく必要がある(whenの引数評価後、内側で新たなmock生成・
        // stubbingを行うと「Unfinished stubbing」として検出されるため)。
        List<TeamActiveMemberCount> activeMemberCounts =
                List.of(mockActiveMemberCount(100L, 1L), mockActiveMemberCount(200L, 2L));

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(availableTeam, fullTeam));
        when(teamMemberRepository.countActiveMembersByTeamIds(List.of(100L, 200L)))
                .thenReturn(activeMemberCounts);

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, null, true);

        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).id());
        verify(teamMemberRepository, times(1)).countActiveMembersByTeamIds(List.of(100L, 200L));
        // available検索の経路では、単一Team用のcountメソッド(応募承認時に使用するもの)は
        // Teamごとに呼ばれない。
        verify(teamMemberRepository, never()).countByTeam_IdAndUser_DeleteFlagFalse(any());
    }

    // GROUP BYで有効なTeamMemberが1人も存在しないTeamは集計結果に含まれないため、
    // 呼び出し側でそのTeamを0人として扱えていることを確認する。
    @Test
    void findTeamsByTournament_available指定時TeamMemberが0人のTeamも0人として空きあり扱いになる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(2);
        Team team = mockFullTeam(100L, tournament, owner);

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));
        when(teamMemberRepository.countActiveMembersByTeamIds(List.of(100L))).thenReturn(List.of());

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, null, true);

        assertEquals(1, responses.size());
    }

    // available=falseは「絞り込まない(全件)」。availableの絞り込みはtrueの時のみ行う。
    // 絞り込みを行わないため、一括集計query自体を呼ぶ必要がない。
    @Test
    void findTeamsByTournament_availableがfalseの場合は絞り込まず一括集計queryも呼ばない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team fullTeam = mockFullTeam(200L, tournament, owner);

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(fullTeam));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, null, false);

        assertEquals(1, responses.size());
        verify(teamMemberRepository, never()).countActiveMembersByTeamIds(any());
        verify(teamMemberRepository, never()).countByTeam_IdAndUser_DeleteFlagFalse(any());
    }

    // available未指定(null)でも絞り込みを行わないため、一括集計queryを呼ばない。
    @Test
    void findTeamsByTournament_available未指定の場合は絞り込まず一括集計queryも呼ばない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, null, null);

        assertEquals(1, responses.size());
        verify(teamMemberRepository, never()).countActiveMembersByTeamIds(any());
    }

    // 「現在有効なメンバー数」の定義統一: 3件のTeamMemberが存在しても1人がdeleteFlag=trueなら
    // 有効人数は2 → 定員2のTeamでも空きありとして対象になる(集計クエリ自体がdelete_flag=falseの
    // Userのみを数える前提で実装されていることの確認。実際のdelete_flag除外自体は
    // TeamMemberRepositoryTestで実DB上確認している)。
    @Test
    void findTeamsByTournament_deleteFlagtrueのメンバーは有効人数に含めないため定員ちょうどでも空きあり扱いになる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(2);
        Team team = mockFullTeam(100L, tournament, owner);

        // 全件(3件、delete_flag=trueのUserを含む)ではなく、有効人数(1件)を返すようstubする。
        List<TeamActiveMemberCount> activeMemberCounts = List.of(mockActiveMemberCount(100L, 1L));

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of(team));
        when(teamMemberRepository.countActiveMembersByTeamIds(List.of(100L))).thenReturn(activeMemberCounts);

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L, null, null, null, true);

        assertEquals(1, responses.size());
    }

    @Test
    void findTeamsByTournament_nameとcharacterIdとrankとavailableを組み合わせた場合_すべての条件で絞り込む() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(2);

        Team matchingTeam = mockFullTeam(100L, tournament, owner);
        lenient().when(matchingTeam.getCharacterRequirements()).thenReturn(List.of(1L));
        lenient().when(matchingTeam.getRankRequirement()).thenReturn("IRON");

        // name検索には一致するが、characterIdで除外されるTeam(available判定まで到達しない)。
        Team nameMatchesButWrongCharacter = mockFullTeam(200L, tournament, owner);
        lenient().when(nameMatchesButWrongCharacter.getCharacterRequirements()).thenReturn(List.of(9L));
        lenient().when(nameMatchesButWrongCharacter.getRankRequirement()).thenReturn("IRON");

        // characterIdフィルタ後に残るのはmatchingTeam(100L)のみのため、一括集計もそのIDのみで呼ばれる。
        List<TeamActiveMemberCount> activeMemberCounts = List.of(mockActiveMemberCount(100L, 1L));

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentIdAndNameContainingIgnoreCase(10L, "ryu"))
                .thenReturn(List.of(matchingTeam, nameMatchesButWrongCharacter));
        when(teamMemberRepository.countActiveMembersByTeamIds(List.of(100L))).thenReturn(activeMemberCounts);

        List<TeamResponse> responses =
                teamService.findTeamsByTournament(10L, "ryu", 1L, "DIAMOND", true);

        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).id());
    }

    @Test
    void findTeamById_正常に取得できる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        TeamResponse response = teamService.findTeamById(100L);

        assertEquals(100L, response.id());
        assertEquals("Test Cup", response.tournamentName());
        assertEquals("Owner User", response.ownerName());
    }

    @Test
    void findTeamById_存在しないTeamの場合_TeamNotFoundExceptionを投げる() {
        when(teamRepository.findActiveTeamById(999L)).thenReturn(Optional.empty());

        assertThrows(TeamNotFoundException.class, () -> teamService.findTeamById(999L));
    }

    @Test
    void findMyTeams_ownerであるTeamとownerではないTeamMemberとして所属するTeamの両方を返す() {
        User user = mockOwner();
        Tournament tournament = mockTournament();

        User otherOwner = mock(User.class);
        lenient().when(otherOwner.getId()).thenReturn(2L);
        lenient().when(otherOwner.getName()).thenReturn("Other Owner");

        // user自身がownerのTeam
        Team ownedTeam = mockFullTeam(100L, tournament, user);
        // userはownerではないが、TeamMemberとして所属しているTeam
        Team joinedTeam = mockFullTeam(200L, tournament, otherOwner);

        TeamMember ownedMembership = mock(TeamMember.class);
        when(ownedMembership.getTeam()).thenReturn(ownedTeam);
        TeamMember joinedMembership = mock(TeamMember.class);
        when(joinedMembership.getTeam()).thenReturn(joinedTeam);

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));
        when(teamMemberRepository.findActiveTeamMembershipsByUserId(1L))
                .thenReturn(List.of(ownedMembership, joinedMembership));

        List<TeamResponse> responses = teamService.findMyTeams(1L);

        List<Long> ids = responses.stream().map(TeamResponse::id).toList();
        assertTrue(ids.contains(100L));
        assertTrue(ids.contains(200L));

        // joinedTeamはownerId=2(user自身ではない)であるにもかかわらず取得できていることから、
        // ownerIdだけを条件にした検索になっていないことを確認する。
        TeamResponse joinedResponse = responses.stream()
                .filter(r -> r.id().equals(200L)).findFirst().orElseThrow();
        assertEquals(2L, joinedResponse.ownerId());
    }

    @Test
    void findMyTeams_Userが存在しない場合_UserNotFoundExceptionを投げる() {
        when(userRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> teamService.findMyTeams(999L));

        verify(teamMemberRepository, never()).findActiveTeamMembershipsByUserId(any());
    }

    private static TeamUpdateRequest allUndefinedTeamUpdateRequest() {
        return new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.undefined());
    }

    @Test
    void updateTeam_正常系の場合_指定項目がすべて更新されflushしてResponseを返す() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(characterRepository.existsById(1L)).thenReturn(true);
        when(characterRepository.existsById(2L)).thenReturn(true);

        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.of("New Team Name"),
                JsonNullable.of("DIAMOND"),
                JsonNullable.of(List.of(1L, 2L)),
                JsonNullable.of("募集メッセージ更新")
        );

        TeamResponse response = teamService.updateTeam(1L, 100L, request);

        assertNotNull(response);
        verify(team).updateName("New Team Name");
        verify(team).updateRankRequirement("DIAMOND");
        verify(team).updateCharacterRequirements(List.of(1L, 2L));
        verify(team).updateRecruitmentMessage("募集メッセージ更新");
        verify(teamRepository, times(1)).flush();
    }

    @Test
    void updateTeam_未指定項目のupdateメソッドは呼ばれずCharacter存在チェックもしない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.of("New Name Only"),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined()
        );

        teamService.updateTeam(1L, 100L, request);

        verify(team).updateName("New Name Only");
        verify(team, never()).updateRankRequirement(any());
        verify(team, never()).updateCharacterRequirements(any());
        verify(team, never()).updateRecruitmentMessage(any());
        verify(characterRepository, never()).existsById(any());
    }

    @Test
    void updateTeam_characterRequirementsが明示的nullの場合_nullで更新されCharacter存在チェックはしない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.of(null), JsonNullable.undefined());

        teamService.updateTeam(1L, 100L, request);

        verify(team).updateCharacterRequirements(null);
        verify(characterRepository, never()).existsById(any());
    }

    @Test
    void updateTeam_characterRequirementsが空リストの場合_空リストで更新されCharacter存在チェックはしない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.of(List.of()), JsonNullable.undefined());

        teamService.updateTeam(1L, 100L, request);

        verify(team).updateCharacterRequirements(List.of());
        verify(characterRepository, never()).existsById(any());
    }

    @Test
    void updateTeam_存在しないTeamの場合_TeamNotFoundExceptionを投げる() {
        when(teamRepository.findActiveTeamById(999L)).thenReturn(Optional.empty());

        assertThrows(
                TeamNotFoundException.class,
                () -> teamService.updateTeam(1L, 999L, allUndefinedTeamUpdateRequest()));

        verify(teamRepository, never()).flush();
    }

    @Test
    void updateTeam_ownerではないUserが更新しようとした場合_NotTeamOwnerExceptionを投げる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        assertThrows(
                NotTeamOwnerException.class,
                () -> teamService.updateTeam(999L, 100L, allUndefinedTeamUpdateRequest()));

        verify(team, never()).updateName(any());
        verify(teamRepository, never()).flush();
    }

    @Test
    void updateTeam_characterRequirementsに存在しないCharacterIdが含まれる場合_InvalidRequestExceptionを投げTeamは更新されない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(characterRepository.existsById(999L)).thenReturn(false);

        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.of(List.of(999L)), JsonNullable.undefined());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> teamService.updateTeam(1L, 100L, request));

        assertEquals("Character not found. character_id=999", exception.getMessage());
        verify(team, never()).updateCharacterRequirements(any());
        verify(teamRepository, never()).flush();
    }

    // rankRequirementをnull(指定なし)へ明示的に更新できる。
    @Test
    void updateTeam_rankRequirementを明示的nullへ更新できる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.of(null),
                JsonNullable.undefined(), JsonNullable.undefined());

        teamService.updateTeam(1L, 100L, request);

        verify(team).updateRankRequirement(null);
        verify(teamRepository, times(1)).flush();
    }

    // rankRequirementがRank enumの値であれば更新できる。
    @Test
    void updateTeam_rankRequirementがRankenumの値の場合_更新できる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.of("ROOKIE"),
                JsonNullable.undefined(), JsonNullable.undefined());

        teamService.updateTeam(1L, 100L, request);

        verify(team).updateRankRequirement("ROOKIE");
        verify(teamRepository, times(1)).flush();
    }

    @Test
    void updateTeam_rankRequirementが不正な値の場合_InvalidRequestExceptionを投げTeamは更新されない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        TeamUpdateRequest request = new TeamUpdateRequest(
                JsonNullable.undefined(), JsonNullable.of("GRANDMASTER"),
                JsonNullable.undefined(), JsonNullable.undefined());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> teamService.updateTeam(1L, 100L, request));

        assertEquals("Invalid rankRequirement specified: GRANDMASTER", exception.getMessage());
        verify(team, never()).updateRankRequirement(any());
        verify(teamRepository, never()).flush();
    }

    @Test
    void findTeamMembers_有効なTeamのメンバー一覧をTeamMemberResponseへ変換して返す() {
        Team team = mockFullTeam(100L, mockTournament(), mockOwner());
        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("Owner User");
        LocalDateTime joinedAt = LocalDateTime.of(2026, 1, 1, 0, 0);

        TeamMember member = mock(TeamMember.class);
        when(member.getUser()).thenReturn(user);
        when(member.getJoinedAt()).thenReturn(joinedAt);

        when(teamMemberRepository.findActiveTeamMembersByTeamId(100L)).thenReturn(List.of(member));

        List<TeamMemberResponse> responses = teamService.findTeamMembers(100L);

        assertEquals(1, responses.size());
        assertEquals(1L, responses.get(0).userId());
        assertEquals("Owner User", responses.get(0).userName());
        assertEquals(joinedAt, responses.get(0).joinedAt());
    }

    @Test
    void findTeamMembers_複数メンバーの場合も取得できる() {
        Team team = mockFullTeam(100L, mockTournament(), mockOwner());
        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        User user1 = mock(User.class);
        when(user1.getId()).thenReturn(1L);
        when(user1.getName()).thenReturn("User One");
        TeamMember member1 = mock(TeamMember.class);
        when(member1.getUser()).thenReturn(user1);
        when(member1.getJoinedAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 0, 0));

        User user2 = mock(User.class);
        when(user2.getId()).thenReturn(2L);
        when(user2.getName()).thenReturn("User Two");
        TeamMember member2 = mock(TeamMember.class);
        when(member2.getUser()).thenReturn(user2);
        when(member2.getJoinedAt()).thenReturn(LocalDateTime.of(2026, 1, 2, 0, 0));

        when(teamMemberRepository.findActiveTeamMembersByTeamId(100L))
                .thenReturn(List.of(member1, member2));

        List<TeamMemberResponse> responses = teamService.findTeamMembers(100L);

        assertEquals(2, responses.size());
        assertEquals(1L, responses.get(0).userId());
        assertEquals("User One", responses.get(0).userName());
        assertEquals(2L, responses.get(1).userId());
        assertEquals("User Two", responses.get(1).userName());
    }

    @Test
    void findTeamMembers_Teamが存在しない場合_TeamNotFoundExceptionを投げTeamMemberRepositoryは呼ばれない() {
        when(teamRepository.findActiveTeamById(999L)).thenReturn(Optional.empty());

        assertThrows(TeamNotFoundException.class, () -> teamService.findTeamMembers(999L));

        verify(teamMemberRepository, never()).findActiveTeamMembersByTeamId(any());
    }

    @Test
    void findTeamMembers_メンバー0人の場合は空Listを返す() {
        Team team = mockFullTeam(100L, mockTournament(), mockOwner());
        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findActiveTeamMembersByTeamId(100L)).thenReturn(List.of());

        List<TeamMemberResponse> responses = teamService.findTeamMembers(100L);

        assertTrue(responses.isEmpty());
    }

    @Test
    void removeTeamMember_ownerが一般メンバーを削除する場合_TeamMemberが削除される() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        TeamMember targetTeamMember = mock(TeamMember.class);
        when(teamMemberRepository.findByTeam_IdAndUser_Id(100L, 2L)).thenReturn(Optional.of(targetTeamMember));

        teamService.removeTeamMember(1L, 100L, 2L);

        verify(teamMemberRepository, times(1)).delete(targetTeamMember);
    }

    @Test
    void removeTeamMember_Teamが存在しない場合_TeamNotFoundExceptionを投げTeamMemberは検索削除されない() {
        when(teamRepository.findActiveTeamById(999L)).thenReturn(Optional.empty());

        assertThrows(
                TeamNotFoundException.class,
                () -> teamService.removeTeamMember(1L, 999L, 2L));

        verify(teamMemberRepository, never()).findByTeam_IdAndUser_Id(any(), any());
        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removeTeamMember_操作Userがownerではない場合_NotTeamOwnerExceptionを投げTeamMemberは検索削除されない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        assertThrows(
                NotTeamOwnerException.class,
                () -> teamService.removeTeamMember(999L, 100L, 2L));

        verify(teamMemberRepository, never()).findByTeam_IdAndUser_Id(any(), any());
        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removeTeamMember_owner自身を削除しようとした場合_CannotRemoveTeamOwnerExceptionを投げTeamMemberは検索削除されない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        assertThrows(
                CannotRemoveTeamOwnerException.class,
                () -> teamService.removeTeamMember(1L, 100L, 1L));

        verify(teamMemberRepository, never()).findByTeam_IdAndUser_Id(any(), any());
        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removeTeamMember_対象TeamMemberが存在しない場合_TeamMemberNotFoundExceptionを投げ削除されない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockFullTeam(100L, tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(100L, 999L)).thenReturn(Optional.empty());

        assertThrows(
                TeamMemberNotFoundException.class,
                () -> teamService.removeTeamMember(1L, 100L, 999L));

        verify(teamMemberRepository, never()).delete(any());
    }
}
