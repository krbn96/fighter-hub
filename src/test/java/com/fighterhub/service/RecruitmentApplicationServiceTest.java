package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fighterhub.dto.RecruitmentApplicationCreateRequest;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.exception.ApplicationAlreadyProcessedException;
import com.fighterhub.exception.CannotApplyToOwnTeamException;
import com.fighterhub.exception.DuplicatePendingApplicationException;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
import com.fighterhub.exception.NotTeamOwnerException;
import com.fighterhub.exception.RecruitmentApplicationNotFoundException;
import com.fighterhub.exception.RecruitmentClosedException;
import com.fighterhub.exception.TeamFullException;
import com.fighterhub.exception.TeamNotFoundException;
import com.fighterhub.repository.RecruitmentApplicationRepository;
import com.fighterhub.repository.TeamMemberRepository;
import com.fighterhub.repository.TeamRepository;
import com.fighterhub.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RecruitmentApplicationServiceTest {

    @Mock
    private RecruitmentApplicationRepository recruitmentApplicationRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private RecruitmentApplicationService recruitmentApplicationService;

    // 募集締切境界値のテスト以外はClockの具体的な値を気にしないため、
    // LocalDateTime.now(clock)がNPEにならないよう最低限のstubをlenientで用意する
    // (isRecruitmentOpen自体はmockTournament()側でany()マッチにより常にtrueを返すようにしている)。
    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(Instant.now());
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private User mockOwner() {
        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(1L);
        lenient().when(owner.getName()).thenReturn("Owner User");
        return owner;
    }

    private User mockApplicant() {
        User applicant = mock(User.class);
        lenient().when(applicant.getId()).thenReturn(2L);
        lenient().when(applicant.getName()).thenReturn("Applicant User");
        return applicant;
    }

    private Tournament mockTournament() {
        Tournament tournament = mock(Tournament.class);
        lenient().when(tournament.getId()).thenReturn(10L);
        // 募集締切の境界値自体を検証するテスト以外は、常に募集中(締切前)として扱う。
        lenient().when(tournament.isRecruitmentOpen(any())).thenReturn(true);
        return tournament;
    }

    private Team mockTeam(Tournament tournament, User owner) {
        Team team = mock(Team.class);
        lenient().when(team.getId()).thenReturn(100L);
        lenient().when(team.getTournament()).thenReturn(tournament);
        lenient().when(team.getOwner()).thenReturn(owner);
        return team;
    }

    private RecruitmentApplication mockSavedApplication(
            Team team, User applicant, String message, LocalDateTime now) {
        RecruitmentApplication application = mock(RecruitmentApplication.class);
        lenient().when(application.getId()).thenReturn(500L);
        lenient().when(application.getTeam()).thenReturn(team);
        lenient().when(application.getUser()).thenReturn(applicant);
        lenient().when(application.getMessage()).thenReturn(message);
        lenient().when(application.getStatus()).thenReturn(RecruitmentApplicationStatus.PENDING);
        lenient().when(application.getCreatedAt()).thenReturn(now);
        lenient().when(application.getUpdatedAt()).thenReturn(now);
        return application;
    }

    // DB取得済み(永続化済み)RecruitmentApplicationを模す。実Entityをspyし、getId()だけを
    // DBが採番した値としてスタブすることで、approve()/reject()の状態遷移ロジックは本物のまま、
    // ID比較による除外判定(rejectPendingApplications)が正しく動作するようにする。
    // RecruitmentApplication.create(...)は永続化前提ではなくgetId()がnullのため、
    // findByIdやfindTargetAndPendingForUpdateOrderByIdがDBから返す「既に永続化済みの行」を
    // そのまま使うとNullPointerException(Long#equalsの呼び出し元がnull)になる。
    private RecruitmentApplication mockPersistedApplication(
            Team team, User applicant, String message, Long id) {
        RecruitmentApplication application = spy(RecruitmentApplication.create(team, applicant, message));
        lenient().doReturn(id).when(application).getId();
        return application;
    }

    // ==================== createApplication ====================

    @Test
    void createApplication_同一Tournament内にPENDINGなしの場合_申請できPENDINGで保存されResponseを返す() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                2L, 10L, RecruitmentApplicationStatus.PENDING)).thenReturn(false);

        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        RecruitmentApplication savedApplication = mockSavedApplication(team, applicant, "よろしくお願いします", now);
        when(recruitmentApplicationRepository.save(any(RecruitmentApplication.class))).thenReturn(savedApplication);

        RecruitmentApplicationCreateRequest request = new RecruitmentApplicationCreateRequest("よろしくお願いします");
        RecruitmentApplicationResponse response = recruitmentApplicationService.createApplication(2L, 100L, request);

        assertEquals(500L, response.id());
        assertEquals(100L, response.teamId());
        assertEquals(2L, response.userId());
        assertEquals("Applicant User", response.userName());
        assertEquals("よろしくお願いします", response.message());
        assertEquals(RecruitmentApplicationStatus.PENDING, response.status());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());

        // Userのlock付き取得が使用されていることを確認する(通常取得は使用しない)。
        verify(userRepository, times(1)).findByIdAndDeleteFlagFalseForUpdate(2L);
        verify(userRepository, never()).findByIdAndDeleteFlagFalse(any());

        ArgumentCaptor<RecruitmentApplication> applicationCaptor =
                ArgumentCaptor.forClass(RecruitmentApplication.class);
        verify(recruitmentApplicationRepository, times(1)).save(applicationCaptor.capture());
        assertEquals(RecruitmentApplicationStatus.PENDING, applicationCaptor.getValue().getStatus());
        assertEquals(team, applicationCaptor.getValue().getTeam());
        assertEquals(applicant, applicationCaptor.getValue().getUser());
    }

    @Test
    void createApplication_messageがnullでも申請できる() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                2L, 10L, RecruitmentApplicationStatus.PENDING)).thenReturn(false);

        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        RecruitmentApplication savedApplication = mockSavedApplication(team, applicant, null, now);
        when(recruitmentApplicationRepository.save(any(RecruitmentApplication.class))).thenReturn(savedApplication);

        RecruitmentApplicationResponse response =
                recruitmentApplicationService.createApplication(2L, 100L, new RecruitmentApplicationCreateRequest(null));

        assertNull(response.message());
    }

    @Test
    void createApplication_Teamが存在しない場合_TeamNotFoundExceptionを投げ後続処理は呼ばれない() {
        when(teamRepository.findActiveTeamById(999L)).thenReturn(Optional.empty());

        assertThrows(
                TeamNotFoundException.class,
                () -> recruitmentApplicationService.createApplication(2L, 999L, new RecruitmentApplicationCreateRequest(null)));

        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(recruitmentApplicationRepository, never())
                .existsByUser_IdAndTeam_Tournament_IdAndStatus(any(), any(), any());
        verify(recruitmentApplicationRepository, never()).save(any());
    }

    @Test
    void createApplication_自分がownerのTeamへ申請しようとした場合_CannotApplyToOwnTeamExceptionを投げ後続処理は呼ばれない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        assertThrows(
                CannotApplyToOwnTeamException.class,
                () -> recruitmentApplicationService.createApplication(1L, 100L, new RecruitmentApplicationCreateRequest(null)));

        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(recruitmentApplicationRepository, never())
                .existsByUser_IdAndTeam_Tournament_IdAndStatus(any(), any(), any());
        verify(recruitmentApplicationRepository, never()).save(any());
    }

    @Test
    void createApplication_TeamMemberとして所属済みの場合_DuplicateTournamentMembershipExceptionを投げ後続処理は呼ばれない() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(true);

        assertThrows(
                DuplicateTournamentMembershipException.class,
                () -> recruitmentApplicationService.createApplication(2L, 100L, new RecruitmentApplicationCreateRequest(null)));

        verify(recruitmentApplicationRepository, never())
                .existsByUser_IdAndTeam_Tournament_IdAndStatus(any(), any(), any());
        verify(recruitmentApplicationRepository, never()).save(any());
    }

    @Test
    void createApplication_同一Tournament内の同じTeamにPENDING申請済みの場合_DuplicatePendingApplicationExceptionを投げる() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                2L, 10L, RecruitmentApplicationStatus.PENDING)).thenReturn(true);

        assertThrows(
                DuplicatePendingApplicationException.class,
                () -> recruitmentApplicationService.createApplication(2L, 100L, new RecruitmentApplicationCreateRequest(null)));

        verify(recruitmentApplicationRepository, never()).save(any());
    }

    @Test
    void createApplication_同一Tournament内の別TeamにPENDING申請済みの場合もDuplicatePendingApplicationExceptionを投げる() {
        // Tournament単位でのexists判定であるため、別Team宛のPENDINGでも同じ結果になることを、
        // 同一のexistsByUser_IdAndTeam_Tournament_IdAndStatus呼び出しで確認する(Team IDは渡さない仕様)。
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                2L, 10L, RecruitmentApplicationStatus.PENDING)).thenReturn(true);

        assertThrows(
                DuplicatePendingApplicationException.class,
                () -> recruitmentApplicationService.createApplication(2L, 100L, new RecruitmentApplicationCreateRequest(null)));

        verify(recruitmentApplicationRepository, never()).save(any());
    }

    @Test
    void createApplication_同一Tournament内にREJECTEDしかない場合は申請できる() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        // REJECTEDのみのためPENDING存在確認はfalse
        when(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                2L, 10L, RecruitmentApplicationStatus.PENDING)).thenReturn(false);

        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        RecruitmentApplication savedApplication = mockSavedApplication(team, applicant, null, now);
        when(recruitmentApplicationRepository.save(any(RecruitmentApplication.class))).thenReturn(savedApplication);

        RecruitmentApplicationResponse response =
                recruitmentApplicationService.createApplication(2L, 100L, new RecruitmentApplicationCreateRequest(null));

        assertEquals(RecruitmentApplicationStatus.PENDING, response.status());
        verify(recruitmentApplicationRepository, times(1)).save(any());
    }

    @Test
    void createApplication_別TournamentにPENDINGがあっても申請できる() {
        // 別Tournamentへの影響がないことは、対象TournamentのみでPENDING存在確認をfalseに
        // スタブすることで表現する(Repository自体は既にRepositoryTestで別Tournament除外を確認済み)。
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                2L, 10L, RecruitmentApplicationStatus.PENDING)).thenReturn(false);

        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        RecruitmentApplication savedApplication = mockSavedApplication(team, applicant, null, now);
        when(recruitmentApplicationRepository.save(any(RecruitmentApplication.class))).thenReturn(savedApplication);

        RecruitmentApplicationResponse response =
                recruitmentApplicationService.createApplication(2L, 100L, new RecruitmentApplicationCreateRequest(null));

        assertEquals(RecruitmentApplicationStatus.PENDING, response.status());
    }

    // 正式business rule: 募集締切(recruitmentDeadline)の境界値を固定Clockで検証する。
    // 締切ちょうど(now == recruitmentDeadline)も不可であることを含む3パターン。
    @Test
    void createApplication_募集締切前の場合_申請できる() {
        LocalDateTime deadline = LocalDateTime.of(2026, 10, 1, 23, 59, 59);
        LocalDateTime now = deadline.minusSeconds(1);
        RecruitmentApplicationService serviceWithFixedClock = serviceWithFixedClock(now);

        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        when(tournament.isRecruitmentOpen(now)).thenReturn(true);
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                2L, 10L, RecruitmentApplicationStatus.PENDING)).thenReturn(false);

        RecruitmentApplication savedApplication = mockSavedApplication(team, applicant, null, now);
        when(recruitmentApplicationRepository.save(any(RecruitmentApplication.class))).thenReturn(savedApplication);

        RecruitmentApplicationResponse response = serviceWithFixedClock.createApplication(
                2L, 100L, new RecruitmentApplicationCreateRequest(null));

        assertEquals(RecruitmentApplicationStatus.PENDING, response.status());
    }

    @Test
    void createApplication_募集締切ちょうどの場合_RecruitmentClosedExceptionを投げ申請は保存されない() {
        LocalDateTime deadline = LocalDateTime.of(2026, 10, 1, 23, 59, 59);
        RecruitmentApplicationService serviceWithFixedClock = serviceWithFixedClock(deadline);

        User owner = mockOwner();
        Tournament tournament = mockTournament();
        when(tournament.isRecruitmentOpen(deadline)).thenReturn(false);
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        assertThrows(
                RecruitmentClosedException.class,
                () -> serviceWithFixedClock.createApplication(
                        2L, 100L, new RecruitmentApplicationCreateRequest(null)));

        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(recruitmentApplicationRepository, never()).save(any());
    }

    @Test
    void createApplication_募集締切後の場合_RecruitmentClosedExceptionを投げ申請は保存されない() {
        LocalDateTime deadline = LocalDateTime.of(2026, 10, 1, 23, 59, 59);
        LocalDateTime now = deadline.plusSeconds(1);
        RecruitmentApplicationService serviceWithFixedClock = serviceWithFixedClock(now);

        User owner = mockOwner();
        Tournament tournament = mockTournament();
        when(tournament.isRecruitmentOpen(now)).thenReturn(false);
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        assertThrows(
                RecruitmentClosedException.class,
                () -> serviceWithFixedClock.createApplication(
                        2L, 100L, new RecruitmentApplicationCreateRequest(null)));

        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(recruitmentApplicationRepository, never()).save(any());
    }

    // JwtProviderTestと同じ手法(Clock.fixed)で、募集締切の境界値を決定的に再現する。
    // 共有の@InjectMocksインスタンス(clockはMockitoモック)とは別に、このテストだけで使う
    // 固定Clock付きインスタンスを都度生成する。
    private RecruitmentApplicationService serviceWithFixedClock(LocalDateTime now) {
        Clock fixedClock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new RecruitmentApplicationService(
                recruitmentApplicationRepository, teamRepository, teamMemberRepository, userRepository, fixedClock);
    }

    // ==================== findTeamApplications / findMyApplications ====================

    @Test
    void findTeamApplications_ownerであれば一覧取得できResponseへ正しく変換される() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        RecruitmentApplication application = mockSavedApplication(team, applicant, "よろしくお願いします", now);
        when(recruitmentApplicationRepository.findByTeamIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(application));

        List<RecruitmentApplicationResponse> responses =
                recruitmentApplicationService.findTeamApplications(1L, 100L);

        assertEquals(1, responses.size());
        RecruitmentApplicationResponse response = responses.get(0);
        assertEquals(500L, response.id());
        assertEquals(100L, response.teamId());
        assertEquals(2L, response.userId());
        assertEquals("Applicant User", response.userName());
        assertEquals("よろしくお願いします", response.message());
        assertEquals(RecruitmentApplicationStatus.PENDING, response.status());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void findTeamApplications_申請が0件の場合は空Listを返す() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findByTeamIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());

        List<RecruitmentApplicationResponse> responses =
                recruitmentApplicationService.findTeamApplications(1L, 100L);

        assertTrue(responses.isEmpty());
    }

    @Test
    void findTeamApplications_Teamが存在しない場合_TeamNotFoundExceptionを投げる() {
        when(teamRepository.findActiveTeamById(999L)).thenReturn(Optional.empty());

        assertThrows(
                TeamNotFoundException.class,
                () -> recruitmentApplicationService.findTeamApplications(1L, 999L));

        verify(recruitmentApplicationRepository, never()).findByTeamIdOrderByCreatedAtDesc(any());
    }

    @Test
    void findTeamApplications_操作Userがownerではない場合_NotTeamOwnerExceptionを投げ一覧Repositoryは呼ばれない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        assertThrows(
                NotTeamOwnerException.class,
                () -> recruitmentApplicationService.findTeamApplications(999L, 100L));

        verify(recruitmentApplicationRepository, never()).findByTeamIdOrderByCreatedAtDesc(any());
    }

    @Test
    void findMyApplications_JWTuserIdに紐づくApplicationだけ取得しResponseへ正しく変換される() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        RecruitmentApplication application = mockSavedApplication(team, applicant, "よろしくお願いします", now);
        when(recruitmentApplicationRepository.findByUser_IdOrderByCreatedAtDesc(2L))
                .thenReturn(List.of(application));

        List<RecruitmentApplicationResponse> responses = recruitmentApplicationService.findMyApplications(2L);

        assertEquals(1, responses.size());
        RecruitmentApplicationResponse response = responses.get(0);
        assertEquals(500L, response.id());
        assertEquals(100L, response.teamId());
        assertEquals(2L, response.userId());
        assertEquals("Applicant User", response.userName());
        assertEquals("よろしくお願いします", response.message());
        assertEquals(RecruitmentApplicationStatus.PENDING, response.status());
    }

    @Test
    void findMyApplications_申請が0件の場合は空Listを返す() {
        when(recruitmentApplicationRepository.findByUser_IdOrderByCreatedAtDesc(2L)).thenReturn(List.of());

        List<RecruitmentApplicationResponse> responses = recruitmentApplicationService.findMyApplications(2L);

        assertTrue(responses.isEmpty());
    }

    // ==================== approveApplication ====================

    @Test
    void approveApplication_ownerがPENDING申請を承認しlock付きUserでTeamMemberが作成されAPPROVEDになる() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        // application.getUser()が返す参照(applicant)とは別の、lock取得で返るUser参照を用意し、
        // TeamMember作成にはこちら(lockedApplicant)が使われることを検証する。
        User lockedApplicant = mock(User.class);
        lenient().when(lockedApplicant.getId()).thenReturn(2L);
        lenient().when(lockedApplicant.getName()).thenReturn("Locked Applicant User");

        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(3);
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = mockPersistedApplication(team, applicant, "よろしくお願いします", 500L);

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                500L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of(application));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(lockedApplicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(teamMemberRepository.countByTeam_IdAndUser_DeleteFlagFalse(100L)).thenReturn(2L);

        RecruitmentApplicationResponse response = recruitmentApplicationService.approveApplication(1L, 100L, 500L);

        assertEquals(RecruitmentApplicationStatus.APPROVED, response.status());
        assertEquals(RecruitmentApplicationStatus.APPROVED, application.getStatus());

        // lock取得順序: Team → RecruitmentApplication(対象+他PENDING一括) → User
        InOrder order = inOrder(teamRepository, recruitmentApplicationRepository, userRepository);
        order.verify(teamRepository).findActiveTeamByIdForUpdate(100L);
        order.verify(recruitmentApplicationRepository)
                .findTargetAndPendingForUpdateOrderById(500L, 10L, RecruitmentApplicationStatus.PENDING);
        order.verify(userRepository).findByIdAndDeleteFlagFalseForUpdate(2L);

        // 事前の非lock読み込み(findById)やfindByIdForUpdate(1行のみのlock)は
        // この経路では使用されていない(persistence contextの汚染・古いEntity再利用を防ぐため)。
        verify(recruitmentApplicationRepository, never()).findById(any());
        verify(recruitmentApplicationRepository, never()).findByIdForUpdate(any());
        verify(teamRepository, never()).findActiveTeamById(any());
        verify(userRepository, never()).findByIdAndDeleteFlagFalse(any());

        // TeamMember作成にはlock取得したUser(lockedApplicant)が使用される。
        ArgumentCaptor<TeamMember> teamMemberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository, times(1)).save(teamMemberCaptor.capture());
        assertEquals(team, teamMemberCaptor.getValue().getTeam());
        assertEquals(lockedApplicant, teamMemberCaptor.getValue().getUser());
    }

    // 正式business rule: 締切前に受け付けたPENDING Applicationは、募集締切後でも承認可能
    // (approveApplicationには募集期限チェックを追加していないことの回帰防止)。
    @Test
    void approveApplication_募集締切後でも承認できる() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        User lockedApplicant = mock(User.class);
        lenient().when(lockedApplicant.getId()).thenReturn(2L);

        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(3);
        // 募集締切後であることを明示する(デフォルトのany()->trueスタブを上書きする)。
        // approveApplicationは募集期限チェックを行わないため実際には呼ばれず、lenientにしている。
        lenient().when(tournament.isRecruitmentOpen(any())).thenReturn(false);
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = mockPersistedApplication(team, applicant, null, 500L);

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                500L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of(application));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(lockedApplicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(teamMemberRepository.countByTeam_IdAndUser_DeleteFlagFalse(100L)).thenReturn(2L);

        RecruitmentApplicationResponse response = recruitmentApplicationService.approveApplication(1L, 100L, 500L);

        assertEquals(RecruitmentApplicationStatus.APPROVED, response.status());
        verify(teamMemberRepository, times(1)).save(any(TeamMember.class));
    }

    // 正式business rule: 同一TournamentでUserがTeamMemberになった時点で、
    // そのUserが同一Tournament内の他TeamへのPENDING申請はREJECTEDへ遷移する。
    @Test
    void approveApplication_同一Tournamentの他PENDING申請はREJECTEDへ遷移し承認対象自身はAPPROVEDになる() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        User lockedApplicant = mock(User.class);
        lenient().when(lockedApplicant.getId()).thenReturn(2L);
        lenient().when(lockedApplicant.getName()).thenReturn("Locked Applicant User");

        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(3);
        Team team = mockTeam(tournament, owner);
        // 承認対象の申請。findTargetAndPendingForUpdateOrderByIdの結果には対象自身(id=500)も
        // 含まれるため、applicationIdによる除外ロジックが正しく働くことを検証する。
        RecruitmentApplication targetApplication =
                mockPersistedApplication(team, applicant, "よろしくお願いします", 500L);

        Team otherTeam = mock(Team.class);
        lenient().when(otherTeam.getId()).thenReturn(200L);
        RecruitmentApplication otherPendingApplication =
                mockPersistedApplication(otherTeam, applicant, "別チームへの申請", 600L);

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                500L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of(targetApplication, otherPendingApplication));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(lockedApplicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(teamMemberRepository.countByTeam_IdAndUser_DeleteFlagFalse(100L)).thenReturn(2L);

        RecruitmentApplicationResponse response = recruitmentApplicationService.approveApplication(1L, 100L, 500L);

        assertEquals(RecruitmentApplicationStatus.APPROVED, response.status());
        assertEquals(RecruitmentApplicationStatus.APPROVED, targetApplication.getStatus());
        assertEquals(RecruitmentApplicationStatus.REJECTED, otherPendingApplication.getStatus());

        // lock取得順序: Team → RecruitmentApplication(対象+他PENDING一括、ID昇順) → User
        InOrder order = inOrder(teamRepository, recruitmentApplicationRepository, userRepository);
        order.verify(teamRepository).findActiveTeamByIdForUpdate(100L);
        order.verify(recruitmentApplicationRepository)
                .findTargetAndPendingForUpdateOrderById(500L, 10L, RecruitmentApplicationStatus.PENDING);
        order.verify(userRepository).findByIdAndDeleteFlagFalseForUpdate(2L);
    }

    // rejectOtherPendingApplicationsInTournament(TeamService#createTeam経由)自体の挙動を
    // 直接検証する。除外対象の申請は存在しないため、該当する全PENDING申請がREJECTEDになる。
    @Test
    void rejectOtherPendingApplicationsInTournament_該当する全PENDING申請がREJECTEDになる() {
        User applicant = mockApplicant();
        Team teamX = mockTeam(mockTournament(), mockOwner());
        RecruitmentApplication pendingToX = mockPersistedApplication(teamX, applicant, null, 500L);

        Team teamY = mock(Team.class);
        lenient().when(teamY.getId()).thenReturn(300L);
        RecruitmentApplication pendingToY = mockPersistedApplication(teamY, applicant, null, 600L);

        when(recruitmentApplicationRepository.findByUser_IdAndTeam_Tournament_IdAndStatusForUpdate(
                2L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of(pendingToX, pendingToY));

        recruitmentApplicationService.rejectOtherPendingApplicationsInTournament(2L, 10L);

        assertEquals(RecruitmentApplicationStatus.REJECTED, pendingToX.getStatus());
        assertEquals(RecruitmentApplicationStatus.REJECTED, pendingToY.getStatus());
        verify(recruitmentApplicationRepository)
                .findByUser_IdAndTeam_Tournament_IdAndStatusForUpdate(
                        2L, 10L, RecruitmentApplicationStatus.PENDING);
    }

    @Test
    void approveApplication_Teamが存在しない場合_TeamNotFoundExceptionを投げ後続lockは取得されない() {
        when(teamRepository.findActiveTeamByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThrows(
                TeamNotFoundException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 999L, 500L));

        verify(recruitmentApplicationRepository, never()).findById(any());
        verify(recruitmentApplicationRepository, never())
                .findTargetAndPendingForUpdateOrderById(any(), any(), any());
        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_操作Userがownerではない場合_NotTeamOwnerExceptionを投げ後続lockは取得されない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));

        assertThrows(
                NotTeamOwnerException.class,
                () -> recruitmentApplicationService.approveApplication(999L, 100L, 500L));

        verify(recruitmentApplicationRepository, never()).findById(any());
        verify(recruitmentApplicationRepository, never())
                .findTargetAndPendingForUpdateOrderById(any(), any(), any());
        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_Applicationが存在しない場合_RecruitmentApplicationNotFoundExceptionを投げUser_lockは取得されない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                500L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of());

        assertThrows(
                RecruitmentApplicationNotFoundException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_Applicationが別Teamに属する場合_RecruitmentApplicationNotFoundExceptionを投げUser_lockは取得されない() {
        User owner = mockOwner();
        User otherOwner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);
        Team otherTeam = mock(Team.class);
        lenient().when(otherTeam.getId()).thenReturn(200L);
        lenient().when(otherTeam.getOwner()).thenReturn(otherOwner);
        RecruitmentApplication application = mockPersistedApplication(otherTeam, applicant, null, 500L);

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                500L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of(application));

        assertThrows(
                RecruitmentApplicationNotFoundException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_Applicationが処理済みの場合_ApplicationAlreadyProcessedExceptionを投げUser_lockは取得されない() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = mockPersistedApplication(team, applicant, null, 500L);
        application.approve();

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));
        // 既にAPPROVED済みのためPENDING検索条件には合致しないが、ra.id一致分で対象自身は返る。
        when(recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                500L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of(application));

        assertThrows(
                ApplicationAlreadyProcessedException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_applicantが同一Tournamentですでに所属している場合_DuplicateTournamentMembershipExceptionを投げTeamMemberは追加されない() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = mockPersistedApplication(team, applicant, null, 500L);

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                500L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of(application));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(true);

        assertThrows(
                DuplicateTournamentMembershipException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        // User lockはこの時点で既に取得済みであること(チェック順が崩れていないこと)を確認する。
        verify(userRepository, times(1)).findByIdAndDeleteFlagFalseForUpdate(2L);
        verify(teamMemberRepository, never()).countByTeam_IdAndUser_DeleteFlagFalse(any());
        verify(teamMemberRepository, never()).save(any());
        assertEquals(RecruitmentApplicationStatus.PENDING, application.getStatus());
    }

    @Test
    void approveApplication_Teamが定員に達している場合_TeamFullExceptionを投げTeamMemberは追加されない() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(3);
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = mockPersistedApplication(team, applicant, null, 500L);

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                500L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of(application));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(teamMemberRepository.countByTeam_IdAndUser_DeleteFlagFalse(100L)).thenReturn(3L);

        assertThrows(
                TeamFullException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        verify(teamMemberRepository, never()).save(any());
        assertEquals(RecruitmentApplicationStatus.PENDING, application.getStatus());
    }

    // 「現在有効なメンバー数」の定義統一の回帰確認: 定員判定にはcountByTeam_Id(全件、
    // deleteFlag=trueのUserも含む)ではなくcountByTeam_IdAndUser_DeleteFlagFalse(有効のみ)を
    // 使用すること自体を確認する(レスポンスの検証だけでは呼び出しメソッドの違いを検出できないため)。
    @Test
    void approveApplication_定員判定にはdeleteFlagを考慮したcountメソッドが使用されcountByTeam_Idは使用されない() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(3);
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = mockPersistedApplication(team, applicant, null, 500L);

        when(teamRepository.findActiveTeamByIdForUpdate(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                500L, 10L, RecruitmentApplicationStatus.PENDING))
                .thenReturn(List.of(application));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L)).thenReturn(Optional.of(applicant));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        // 有効人数カウント(countByTeam_IdAndUser_DeleteFlagFalse)が2(定員3未満、空きあり)を
        // 返せばapproveは成功する。countByTeam_Id(全件カウント)は一切スタブしておらず、
        // production codeがこちらを呼び出せばUnnecessaryStubbingException等にはならないが
        // 戻り値はMockitoのデフォルト(0)になるため、below never()での未使用確認と合わせて
        // 「実際に使用されるのはcountByTeam_IdAndUser_DeleteFlagFalseのみ」であることを確認する。
        when(teamMemberRepository.countByTeam_IdAndUser_DeleteFlagFalse(100L)).thenReturn(2L);

        RecruitmentApplicationResponse response = recruitmentApplicationService.approveApplication(1L, 100L, 500L);

        assertEquals(RecruitmentApplicationStatus.APPROVED, response.status());
        verify(teamMemberRepository, times(1)).save(any(TeamMember.class));
        verify(teamMemberRepository, never()).countByTeam_Id(any());
    }

    // ==================== rejectApplication ====================

    @Test
    void rejectApplication_ownerがPENDING申請をREJECTEDにできTeamMemberは追加されずTeamUser_lockも取得されない() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = RecruitmentApplication.create(team, applicant, "よろしくお願いします");

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(application));

        RecruitmentApplicationResponse response = recruitmentApplicationService.rejectApplication(1L, 100L, 500L);

        assertEquals(RecruitmentApplicationStatus.REJECTED, response.status());
        assertEquals(RecruitmentApplicationStatus.REJECTED, application.getStatus());

        // Teamは通常取得(lockなし)、Applicationはlock付き取得。
        verify(teamRepository, times(1)).findActiveTeamById(100L);
        verify(teamRepository, never()).findActiveTeamByIdForUpdate(any());
        verify(recruitmentApplicationRepository, times(1)).findByIdForUpdate(500L);
        // Team定員・TeamMember・User所属は変更しないため、User/TeamのWRITE LOCKや関連処理は行わない。
        verify(userRepository, never()).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(teamMemberRepository, never()).save(any());
        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(teamMemberRepository, never()).countByTeam_IdAndUser_DeleteFlagFalse(any());
    }

    // 正式business rule: 締切前に受け付けたPENDING Applicationは、募集締切後でも拒否可能
    // (rejectApplicationには募集期限チェックを追加していないことの回帰防止)。
    @Test
    void rejectApplication_募集締切後でも拒否できる() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        // 募集締切後であることを明示する(デフォルトのany()->trueスタブを上書きする)。
        // rejectApplicationは募集期限チェックを行わないため実際には呼ばれず、lenientにしている。
        lenient().when(tournament.isRecruitmentOpen(any())).thenReturn(false);
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = RecruitmentApplication.create(team, applicant, null);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(application));

        RecruitmentApplicationResponse response = recruitmentApplicationService.rejectApplication(1L, 100L, 500L);

        assertEquals(RecruitmentApplicationStatus.REJECTED, response.status());
    }

    @Test
    void rejectApplication_Teamが存在しない場合_TeamNotFoundExceptionを投げる() {
        when(teamRepository.findActiveTeamById(999L)).thenReturn(Optional.empty());

        assertThrows(
                TeamNotFoundException.class,
                () -> recruitmentApplicationService.rejectApplication(1L, 999L, 500L));

        verify(recruitmentApplicationRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectApplication_操作Userがownerではない場合_NotTeamOwnerExceptionを投げる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        assertThrows(
                NotTeamOwnerException.class,
                () -> recruitmentApplicationService.rejectApplication(999L, 100L, 500L));

        verify(recruitmentApplicationRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectApplication_Applicationが存在しない場合_RecruitmentApplicationNotFoundExceptionを投げる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findByIdForUpdate(500L)).thenReturn(Optional.empty());

        assertThrows(
                RecruitmentApplicationNotFoundException.class,
                () -> recruitmentApplicationService.rejectApplication(1L, 100L, 500L));
    }

    @Test
    void rejectApplication_Applicationが別Teamに属する場合_RecruitmentApplicationNotFoundExceptionを投げる() {
        User owner = mockOwner();
        User otherOwner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);
        Team otherTeam = mock(Team.class);
        lenient().when(otherTeam.getId()).thenReturn(200L);
        lenient().when(otherTeam.getOwner()).thenReturn(otherOwner);
        RecruitmentApplication application = RecruitmentApplication.create(otherTeam, applicant, null);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(application));

        assertThrows(
                RecruitmentApplicationNotFoundException.class,
                () -> recruitmentApplicationService.rejectApplication(1L, 100L, 500L));
    }

    @Test
    void rejectApplication_Applicationが処理済みの場合_ApplicationAlreadyProcessedExceptionを投げる() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = RecruitmentApplication.create(team, applicant, null);
        application.reject();

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(application));

        assertThrows(
                ApplicationAlreadyProcessedException.class,
                () -> recruitmentApplicationService.rejectApplication(1L, 100L, 500L));
    }
}
