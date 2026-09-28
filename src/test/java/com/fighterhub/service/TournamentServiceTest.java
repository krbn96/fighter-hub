package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.openapitools.jackson.nullable.JsonNullable;

import com.fighterhub.dto.TournamentCreateRequest;
import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.dto.TournamentUpdateRequest;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.entity.UserRole;
import com.fighterhub.exception.NotAdminException;
import com.fighterhub.exception.TournamentNotFoundException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.TournamentRepository;
import com.fighterhub.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TournamentService tournamentService;

    private User mockUser(UserRole role) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(1L);
        lenient().when(user.getRole()).thenReturn(role);
        return user;
    }

    private static TournamentCreateRequest sampleCreateRequest() {
        return new TournamentCreateRequest(
                "STREET FIGHTER 6 CUP",
                3,
                LocalDateTime.of(2026, 10, 1, 19, 0),
                64,
                "OPEN"
        );
    }

    private static TournamentUpdateRequest allUndefinedUpdateRequest() {
        return new TournamentUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.undefined());
    }

    private Tournament newRealTournament() {
        return Tournament.create(
                "STREET FIGHTER 6 CUP", 3, LocalDateTime.of(2026, 10, 1, 19, 0), 64, "OPEN");
    }

    private Tournament mockTournament() {
        Tournament tournament = mock(Tournament.class);
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 1, 19, 0);
        when(tournament.getId()).thenReturn(1L);
        when(tournament.getName()).thenReturn("STREET FIGHTER 6 CUP");
        when(tournament.getTeamSize()).thenReturn(3);
        when(tournament.getStartAt()).thenReturn(startAt);
        when(tournament.getMaxPlayers()).thenReturn(64);
        when(tournament.getStatus()).thenReturn("OPEN");
        when(tournament.getCreatedAt()).thenReturn(now);
        when(tournament.getUpdatedAt()).thenReturn(now);
        return tournament;
    }

    @Test
    void findAllTournaments_有効なTournamentをResponseへ変換して返す() {
        Tournament tournament = mockTournament();
        when(tournamentRepository.findAllByDeleteFlagFalse()).thenReturn(List.of(tournament));

        List<TournamentResponse> responses = tournamentService.findAllTournaments();

        assertEquals(1, responses.size());
        TournamentResponse response = responses.get(0);
        assertEquals(1L, response.id());
        assertEquals("STREET FIGHTER 6 CUP", response.name());
        assertEquals(3, response.teamSize());
        assertEquals(64, response.maxPlayers());
        assertEquals("OPEN", response.status());
    }

    @Test
    void findAllTournaments_0件の場合は空Listを返す() {
        when(tournamentRepository.findAllByDeleteFlagFalse()).thenReturn(List.of());

        List<TournamentResponse> responses = tournamentService.findAllTournaments();

        assertEquals(0, responses.size());
    }

    @Test
    void findTournamentById_正常に取得できる() {
        Tournament tournament = mockTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = tournamentService.findTournamentById(1L);

        assertEquals(1L, response.id());
        assertEquals("STREET FIGHTER 6 CUP", response.name());
    }

    @Test
    void findTournamentById_存在しない場合_TournamentNotFoundExceptionを投げる() {
        when(tournamentRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(TournamentNotFoundException.class, () -> tournamentService.findTournamentById(999L));
    }

    @Test
    void createTournament_ADMINならTournamentを作成できる() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        Tournament savedTournament = mockTournament();
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(savedTournament);

        TournamentResponse response = tournamentService.createTournament(1L, sampleCreateRequest());

        assertEquals(1L, response.id());
        assertEquals("STREET FIGHTER 6 CUP", response.name());
        assertEquals(3, response.teamSize());
        assertEquals(64, response.maxPlayers());
        assertEquals("OPEN", response.status());

        ArgumentCaptor<Tournament> tournamentCaptor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository, times(1)).save(tournamentCaptor.capture());
        assertEquals("STREET FIGHTER 6 CUP", tournamentCaptor.getValue().getName());
        assertEquals(3, tournamentCaptor.getValue().getTeamSize());
        assertEquals(64, tournamentCaptor.getValue().getMaxPlayers());
        assertEquals("OPEN", tournamentCaptor.getValue().getStatus());
    }

    @Test
    void createTournament_USERの場合_NotAdminExceptionを投げsaveは呼ばれない() {
        User user = mockUser(UserRole.USER);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        assertThrows(
                NotAdminException.class,
                () -> tournamentService.createTournament(1L, sampleCreateRequest()));

        verify(tournamentRepository, never()).save(any());
    }

    @Test
    void createTournament_Userが存在しない場合_UserNotFoundExceptionを投げsaveは呼ばれない() {
        when(userRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> tournamentService.createTournament(999L, sampleCreateRequest()));

        verify(tournamentRepository, never()).save(any());
    }

    @Test
    void updateTournament_ADMINなら指定フィールドを更新でき未指定フィールドは変更されない() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        Tournament tournament = newRealTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(100L)).thenReturn(Optional.of(tournament));

        TournamentUpdateRequest request = new TournamentUpdateRequest(
                JsonNullable.of("New Cup Name"),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined()
        );

        TournamentResponse response = tournamentService.updateTournament(1L, 100L, request);

        assertEquals("New Cup Name", response.name());
        // 未指定フィールドは変更されない。
        assertEquals(3, response.teamSize());
        assertEquals(64, response.maxPlayers());
        assertEquals("OPEN", response.status());
        verify(tournamentRepository, times(1)).flush();
    }

    @Test
    void updateTournament_USERの場合_NotAdminExceptionを投げTournament取得もflushも行われない() {
        User user = mockUser(UserRole.USER);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        assertThrows(
                NotAdminException.class,
                () -> tournamentService.updateTournament(1L, 100L, allUndefinedUpdateRequest()));

        verify(tournamentRepository, never()).findByIdAndDeleteFlagFalse(any());
        verify(tournamentRepository, never()).flush();
    }

    @Test
    void updateTournament_Tournamentが存在しない場合_TournamentNotFoundExceptionを投げる() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(
                TournamentNotFoundException.class,
                () -> tournamentService.updateTournament(1L, 999L, allUndefinedUpdateRequest()));

        verify(tournamentRepository, never()).flush();
    }

    @Test
    void deleteTournament_ADMINならdeleteFlagがtrueになりRepositoryのdeleteは使用されない() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        Tournament tournament = newRealTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(100L)).thenReturn(Optional.of(tournament));

        tournamentService.deleteTournament(1L, 100L);

        assertTrue(tournament.isDeleteFlag());
        verify(tournamentRepository, never()).delete(any());
        verify(tournamentRepository, never()).deleteById(any());
    }

    @Test
    void deleteTournament_USERの場合_NotAdminExceptionを投げTournamentは取得されない() {
        User user = mockUser(UserRole.USER);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        assertThrows(
                NotAdminException.class,
                () -> tournamentService.deleteTournament(1L, 100L));

        verify(tournamentRepository, never()).findByIdAndDeleteFlagFalse(any());
    }

    @Test
    void deleteTournament_Tournamentが存在しないまたは既削除の場合_TournamentNotFoundExceptionを投げる() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(
                TournamentNotFoundException.class,
                () -> tournamentService.deleteTournament(1L, 999L));
    }
}
