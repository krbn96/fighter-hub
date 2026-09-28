package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.entity.Tournament;
import com.fighterhub.exception.TournamentNotFoundException;
import com.fighterhub.repository.TournamentRepository;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock
    private TournamentRepository tournamentRepository;

    @InjectMocks
    private TournamentService tournamentService;

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
}
