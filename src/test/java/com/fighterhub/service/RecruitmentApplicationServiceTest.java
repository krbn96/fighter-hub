package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fighterhub.dto.RecruitmentApplicationCreateRequest;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.exception.ApplicationAlreadyProcessedException;
import com.fighterhub.exception.CannotApplyToOwnTeamException;
import com.fighterhub.exception.DuplicatePendingApplicationException;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
import com.fighterhub.exception.NotTeamOwnerException;
import com.fighterhub.exception.RecruitmentApplicationNotFoundException;
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

    @InjectMocks
    private RecruitmentApplicationService recruitmentApplicationService;

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

    @Test
    void createApplication_有効なTeamへ申請できPENDINGで保存されResponseを返す() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(recruitmentApplicationRepository.existsByTeam_IdAndUser_IdAndStatus(
                100L, 2L, RecruitmentApplicationStatus.PENDING)).thenReturn(false);
        when(userRepository.findByIdAndDeleteFlagFalse(2L)).thenReturn(Optional.of(applicant));

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
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(recruitmentApplicationRepository.existsByTeam_IdAndUser_IdAndStatus(
                100L, 2L, RecruitmentApplicationStatus.PENDING)).thenReturn(false);
        when(userRepository.findByIdAndDeleteFlagFalse(2L)).thenReturn(Optional.of(applicant));

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

        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(recruitmentApplicationRepository, never()).existsByTeam_IdAndUser_IdAndStatus(any(), any(), any());
        verify(userRepository, never()).findByIdAndDeleteFlagFalse(any());
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

        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(recruitmentApplicationRepository, never()).existsByTeam_IdAndUser_IdAndStatus(any(), any(), any());
        verify(userRepository, never()).findByIdAndDeleteFlagFalse(any());
        verify(recruitmentApplicationRepository, never()).save(any());
    }

    @Test
    void createApplication_同一Tournamentですでにチームに所属している場合_DuplicateTournamentMembershipExceptionを投げ後続処理は呼ばれない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(true);

        assertThrows(
                DuplicateTournamentMembershipException.class,
                () -> recruitmentApplicationService.createApplication(2L, 100L, new RecruitmentApplicationCreateRequest(null)));

        verify(recruitmentApplicationRepository, never()).existsByTeam_IdAndUser_IdAndStatus(any(), any(), any());
        verify(userRepository, never()).findByIdAndDeleteFlagFalse(any());
        verify(recruitmentApplicationRepository, never()).save(any());
    }

    @Test
    void createApplication_同じTeamへPENDING申請済みの場合_DuplicatePendingApplicationExceptionを投げ後続処理は呼ばれない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(recruitmentApplicationRepository.existsByTeam_IdAndUser_IdAndStatus(
                100L, 2L, RecruitmentApplicationStatus.PENDING)).thenReturn(true);

        assertThrows(
                DuplicatePendingApplicationException.class,
                () -> recruitmentApplicationService.createApplication(2L, 100L, new RecruitmentApplicationCreateRequest(null)));

        verify(userRepository, never()).findByIdAndDeleteFlagFalse(any());
        verify(recruitmentApplicationRepository, never()).save(any());
    }

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

    @Test
    void approveApplication_ownerがPENDING申請を承認しTeamMemberが作成されAPPROVEDになる() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        when(tournament.getTeamSize()).thenReturn(3);
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = RecruitmentApplication.create(team, applicant, "よろしくお願いします");

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.of(application));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(teamMemberRepository.countByTeam_Id(100L)).thenReturn(2L);

        RecruitmentApplicationResponse response = recruitmentApplicationService.approveApplication(1L, 100L, 500L);

        assertEquals(RecruitmentApplicationStatus.APPROVED, response.status());
        assertEquals(RecruitmentApplicationStatus.APPROVED, application.getStatus());

        ArgumentCaptor<TeamMember> teamMemberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository, times(1)).save(teamMemberCaptor.capture());
        assertEquals(team, teamMemberCaptor.getValue().getTeam());
        assertEquals(applicant, teamMemberCaptor.getValue().getUser());
    }

    @Test
    void approveApplication_Teamが存在しない場合_TeamNotFoundExceptionを投げ後続処理は呼ばれない() {
        when(teamRepository.findActiveTeamById(999L)).thenReturn(Optional.empty());

        assertThrows(
                TeamNotFoundException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 999L, 500L));

        verify(recruitmentApplicationRepository, never()).findById(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_操作Userがownerではない場合_NotTeamOwnerExceptionを投げ後続処理は呼ばれない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));

        assertThrows(
                NotTeamOwnerException.class,
                () -> recruitmentApplicationService.approveApplication(999L, 100L, 500L));

        verify(recruitmentApplicationRepository, never()).findById(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_Applicationが存在しない場合_RecruitmentApplicationNotFoundExceptionを投げ後続処理は呼ばれない() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.empty());

        assertThrows(
                RecruitmentApplicationNotFoundException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_Applicationが別Teamに属する場合_RecruitmentApplicationNotFoundExceptionを投げ後続処理は呼ばれない() {
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
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.of(application));

        assertThrows(
                RecruitmentApplicationNotFoundException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_Applicationが処理済みの場合_ApplicationAlreadyProcessedExceptionを投げ後続処理は呼ばれない() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = RecruitmentApplication.create(team, applicant, null);
        application.approve();

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.of(application));

        assertThrows(
                ApplicationAlreadyProcessedException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        verify(teamMemberRepository, never()).existsByUser_IdAndTeam_Tournament_Id(any(), any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void approveApplication_applicantが同一Tournamentですでに所属している場合_DuplicateTournamentMembershipExceptionを投げTeamMemberは追加されない() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = RecruitmentApplication.create(team, applicant, null);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.of(application));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(true);

        assertThrows(
                DuplicateTournamentMembershipException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        verify(teamMemberRepository, never()).countByTeam_Id(any());
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
        RecruitmentApplication application = RecruitmentApplication.create(team, applicant, null);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.of(application));
        when(teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(2L, 10L)).thenReturn(false);
        when(teamMemberRepository.countByTeam_Id(100L)).thenReturn(3L);

        assertThrows(
                TeamFullException.class,
                () -> recruitmentApplicationService.approveApplication(1L, 100L, 500L));

        verify(teamMemberRepository, never()).save(any());
        assertEquals(RecruitmentApplicationStatus.PENDING, application.getStatus());
    }

    @Test
    void rejectApplication_ownerがPENDING申請をREJECTEDにできTeamMemberは追加されない() {
        User owner = mockOwner();
        User applicant = mockApplicant();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);
        RecruitmentApplication application = RecruitmentApplication.create(team, applicant, "よろしくお願いします");

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.of(application));

        RecruitmentApplicationResponse response = recruitmentApplicationService.rejectApplication(1L, 100L, 500L);

        assertEquals(RecruitmentApplicationStatus.REJECTED, response.status());
        assertEquals(RecruitmentApplicationStatus.REJECTED, application.getStatus());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void rejectApplication_Teamが存在しない場合_TeamNotFoundExceptionを投げる() {
        when(teamRepository.findActiveTeamById(999L)).thenReturn(Optional.empty());

        assertThrows(
                TeamNotFoundException.class,
                () -> recruitmentApplicationService.rejectApplication(1L, 999L, 500L));

        verify(recruitmentApplicationRepository, never()).findById(any());
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

        verify(recruitmentApplicationRepository, never()).findById(any());
    }

    @Test
    void rejectApplication_Applicationが存在しない場合_RecruitmentApplicationNotFoundExceptionを投げる() {
        User owner = mockOwner();
        Tournament tournament = mockTournament();
        Team team = mockTeam(tournament, owner);

        when(teamRepository.findActiveTeamById(100L)).thenReturn(Optional.of(team));
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.empty());

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
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.of(application));

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
        when(recruitmentApplicationRepository.findById(500L)).thenReturn(Optional.of(application));

        assertThrows(
                ApplicationAlreadyProcessedException.class,
                () -> recruitmentApplicationService.rejectApplication(1L, 100L, 500L));
    }
}
