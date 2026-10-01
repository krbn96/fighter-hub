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

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L);

        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).id());
        assertEquals("Test Cup", responses.get(0).tournamentName());
    }

    @Test
    void findTeamsByTournament_Tournamentが存在しない場合_TournamentNotFoundExceptionを投げる() {
        when(tournamentRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(
                TournamentNotFoundException.class,
                () -> teamService.findTeamsByTournament(999L));

        verify(teamRepository, never()).findActiveTeamsByTournamentId(any());
    }

    @Test
    void findTeamsByTournament_該当Teamが0件の場合は空Listを返す() {
        Tournament tournament = mockTournament();

        when(tournamentRepository.findByIdAndDeleteFlagFalse(10L)).thenReturn(Optional.of(tournament));
        when(teamRepository.findActiveTeamsByTournamentId(10L)).thenReturn(List.of());

        List<TeamResponse> responses = teamService.findTeamsByTournament(10L);

        assertTrue(responses.isEmpty());
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
