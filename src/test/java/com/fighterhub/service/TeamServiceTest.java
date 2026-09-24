package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.fighterhub.dto.TeamCreateRequest;
import com.fighterhub.dto.TeamCreateResponse;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
import com.fighterhub.exception.InvalidRequestException;
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

    @InjectMocks
    private TeamService teamService;

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
        return owner;
    }

    private Tournament mockTournament() {
        Tournament tournament = mock(Tournament.class);
        lenient().when(tournament.getId()).thenReturn(10L);
        return tournament;
    }

    private Team mockSavedTeam(Tournament tournament, User owner) {
        Team savedTeam = mock(Team.class);
        lenient().when(savedTeam.getTournament()).thenReturn(tournament);
        lenient().when(savedTeam.getOwner()).thenReturn(owner);
        return savedTeam;
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
}
